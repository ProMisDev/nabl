package mb.statix.spec;

import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermPattern.P;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.metaborg.util.functions.Action1;
import org.metaborg.util.functions.PartialFunction1;
import org.metaborg.util.functions.Predicate1;
import org.metaborg.util.functions.Predicate2;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.SetMultimap;

import io.usethesource.capsule.Set;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.matching.Pattern;
import mb.nabl2.terms.substitution.FreshVars;
import mb.nabl2.terms.substitution.IRenaming;
import mb.nabl2.terms.unification.OccursException;
import mb.nabl2.terms.unification.u.IUnifier;
import mb.nabl2.terms.unification.ud.Diseq;
import mb.nabl2.terms.unification.ud.IUniDisunifier;
import mb.nabl2.terms.unification.ud.PersistentUniDisunifier;
import mb.nabl2.util.CapsuleUtil;
import mb.nabl2.util.Tuple2;
import mb.nabl2.util.Tuple3;
import mb.statix.constraints.CConj;
import mb.statix.constraints.CEqual;
import mb.statix.constraints.CExists;
import mb.statix.constraints.CUser;
import mb.statix.constraints.Constraints;
import mb.statix.solver.IConstraint;
import mb.statix.solver.StateUtil;
import mb.statix.spec.ApplyMode.Safety;

public class RuleUtil {

    /**
     * Apply the given list of rules to the given arguments. Returns the result of application if one rule can be
     * applied, empty of empty of no rules apply, or empty if more rules applied. Rules are expected to be in matching
     * order, with the first rule that can be applied is selected if the match is unconditional, or it is the only rule
     * that can be applied.
     *
     * @param state
     *            Initial state
     * @param rules
     *            Ordered list of rules to apply
     * @param args
     *            Arguments to apply the rules to
     * @param cause
     *            Cause of this rule application, null if none
     *
     * @return Some application result if one rule applies, some empty if no rules apply, and empty if multiple rules
     *         apply.
     */
    public static <E extends Throwable> Optional<Optional<Tuple2<Rule, ApplyResult>>> applyOrderedOne(
            IUniDisunifier.Immutable state, List<Rule> rules, List<? extends ITerm> args, @Nullable IConstraint cause,
            ApplyMode<E> mode, Safety safety) throws E {
        return applyOrdered(state, rules, args, cause, true, mode, safety)
                .map(rs -> rs.stream().collect(MoreCollectors.toOptional()));
    }

    /**
     * Apply the given list of rules to the given arguments. Returns application results for rules can be applied. Rules
     * are expected to be in matching order, with rules being selected up to and including the first rule that can be
     * applied unconditional.
     *
     * @param state
     *            Initial state
     * @param rules
     *            Ordered list of rules to apply
     * @param args
     *            Arguments to apply the rules to
     * @param cause
     *            Cause of this rule application, null if none
     *
     * @return A list of apply results, up to and including the first unconditionally matching result.
     */
    public static <E extends Throwable> List<Tuple2<Rule, ApplyResult>> applyOrderedAll(IUniDisunifier.Immutable state,
            List<Rule> rules, List<? extends ITerm> args, @Nullable IConstraint cause, ApplyMode<E> mode, Safety safety)
            throws E {
        return applyOrdered(state, rules, args, cause, false, mode, safety).get();
    }

    /**
     * Helper method to apply the given list of ordered rules to the given arguments. Returns a list of results for all
     * rules that could be applied, or empty if onlyOne is true, and multiple matches were found.
     */
    private static <E extends Throwable> Optional<List<Tuple2<Rule, ApplyResult>>> applyOrdered(
            IUniDisunifier.Immutable unifier, List<Rule> rules, List<? extends ITerm> args, @Nullable IConstraint cause,
            boolean onlyOne, ApplyMode<E> mode, Safety safety) throws E {
        final ImmutableList.Builder<Tuple2<Rule, ApplyResult>> results = ImmutableList.builder();
        final AtomicBoolean foundOne = new AtomicBoolean(false);
        for(Rule rule : rules) {
            // apply rule
            final ApplyResult applyResult;
            if((applyResult = apply(unifier, rule, args, cause, mode, safety).orElse(null)) == null) {
                // this rule does not apply, continue to next rules
                continue;
            }
            if(onlyOne && foundOne.getAndSet(true)) {
                // we require exactly one, but found multiple
                return Optional.empty();
            }
            results.add(Tuple2.of(rule, applyResult));

            // stop or add guard to state for next rule
            final Tuple3<Set<ITermVar>, ITerm, ITerm> guard;
            if((guard = applyResult.guard().map(Diseq::toTuple).orElse(null)) == null) {
                // next rules are unreachable after this unconditional match
                break;
            }
            final Optional<IUniDisunifier.Immutable> newUnifier =
                    unifier.disunify(guard._1(), guard._2(), guard._3()).map(IUniDisunifier.Result::unifier);
            if(!newUnifier.isPresent()) {
                // guards are equalities missing in the unifier, disunifying them should never fail
                throw new IllegalStateException("Unexpected incompatible guard.");
            }
            unifier = newUnifier.get();
        }
        return Optional.of(results.build());
    }

    /**
     * Apply the given rule to the given arguments. Returns the result of application, or nothing of the rule cannot be
     * applied. The result may contain equalities that need to be satisfied for the application to be valid.
     */
    public static <E extends Throwable> Optional<ApplyResult> apply(IUniDisunifier.Immutable unifier, Rule rule,
            List<? extends ITerm> args, @Nullable IConstraint cause, ApplyMode<E> mode, Safety safety) throws E {
        return mode.apply(unifier, rule, args, cause, safety);
    }

    /**
     * Apply the given rules to the given arguments. Returns the results of application.
     */
    public static <E extends Throwable> List<Tuple2<Rule, ApplyResult>> applyAll(IUniDisunifier.Immutable state,
            Collection<Rule> rules, List<? extends ITerm> args, @Nullable IConstraint cause, ApplyMode<E> mode,
            Safety safety) throws E {
        final ImmutableList.Builder<Tuple2<Rule, ApplyResult>> results = ImmutableList.builder();
        for(Rule rule : rules) {
            final ApplyResult result;
            if((result = apply(state, rule, args, cause, mode, safety).orElse(null)) != null) {
                results.add(Tuple2.of(rule, result));
            }
        }
        return results.build();
    }

    /**
     * Computes the order independent rules.
     *
     * @param rules
     *            the ordered set of rules for which to compute
     * @return the set of order independent rules
     */
    public static ImmutableSet<Rule> computeOrderIndependentRules(List<Rule> rules) {
        final ImmutableSet.Builder<Rule> newRules = ImmutableSet.builder();
        final List<Tuple3<Set.Immutable<ITermVar>, ITerm, IUnifier.Immutable>> guards = new ArrayList<>();
        RULE: for(Rule rule : rules) {
            final Set.Immutable<ITermVar> ruleParamVars = rule.paramVars();
            final FreshVars fresh = new FreshVars(rule.freeVars(), ruleParamVars);

            final List<ITerm> paramTerms = new ArrayList<>();
            final IUniDisunifier.Transient _paramsUnifier = PersistentUniDisunifier.Immutable.of().melt();
            for(Pattern param : rule.params()) {
                final Tuple2<ITerm, List<Tuple2<ITermVar, ITerm>>> paramTerm =
                        param.asTerm(v -> v.orElseGet(() -> fresh.fresh("_")));
                paramTerms.add(paramTerm._1());
                try {
                    if(!_paramsUnifier.unify(paramTerm._2()).isPresent()) {
                        continue RULE; // skip, unmatchable pattern
                    }
                } catch(OccursException ex) {
                    continue RULE; // skip, unmatchable pattern
                }
            }
            final Set.Immutable<ITermVar> paramVars = fresh.fix().__insertAll(ruleParamVars);
            final ITerm paramsTerm = B.newTuple(paramTerms);
            final IUniDisunifier.Immutable paramsUnifier = _paramsUnifier.freeze();

            final IUniDisunifier.Transient _unifier = paramsUnifier.melt();
            GUARD: for(Tuple3<Set.Immutable<ITermVar>, ITerm, IUnifier.Immutable> guard : guards) {
                final IRenaming guardRen = fresh.fresh(guard._1());
                final Set.Immutable<ITermVar> guardVars = fresh.reset();
                final ITerm guardTerm = guardRen.apply(guard._2());
                IUnifier.Immutable guardUnifier = guard._3().rename(guardRen);
                try {
                    if((guardUnifier =
                            guardUnifier.unify(paramsTerm, guardTerm).map(r -> r.unifier()).orElse(null)) == null) {
                        continue GUARD; // skip, guard already satisfied
                    }
                } catch(OccursException ex) {
                    continue GUARD; // skip, guard already satisfied
                }
                if(!_unifier.disunify(guardVars, guardUnifier).isPresent()) {
                    continue RULE; // skip, incompatible patterns & guards
                }
            }
            final IUniDisunifier.Immutable unifier = _unifier.freeze();

            final Tuple3<Set.Immutable<ITermVar>, ITerm, IUnifier.Immutable> guard =
                    Tuple3.of(paramVars, paramsTerm, paramsUnifier);
            guards.add(guard);

            final List<Pattern> params = paramTerms.stream().map(P::fromTerm).collect(ImmutableList.toImmutableList());

            // we initialized FreshVars to make sure these do not capture any free variables,
            // or shadow any pattern variables. We can therefore use the original body without any
            // renaming
            final Set.Immutable<ITermVar> newBodyVars = paramVars.__removeAll(paramsTerm.getVars());
            final IConstraint body =
                    Constraints.exists(newBodyVars, Constraints.conjoin(StateUtil.asConstraint(unifier), rule.body()));

            final Rule newRule = Rule.builder().from(rule).params(params).body(body).build();

            newRules.add(newRule);
        }
        return newRules.build();
    }

    /*
    public static ImmutableSet<Rule> computeOrderIndependentRules_(List<Rule> rules) {
        final List<Pattern> guards = Lists.newArrayList();
    
        return rules.stream().flatMap(r -> {
            final IUniDisunifier.Transient diseqs = PersistentUniDisunifier.Immutable.of().melt();
    
            // Eliminate wildcards in the patterns
            final FreshVars fresh = new FreshVars(vars(r));
            final List<Pattern> paramPatterns = r.params().stream().map(p -> p.eliminateWld(() -> fresh.fresh("_")))
                    .collect(ImmutableList.toImmutableList());
            fresh.fix();
            final Pattern paramsPattern = P.newTuple(paramPatterns);
    
            // Create term for params and add implied equalities
            final Tuple2<ITerm, List<Tuple2<ITermVar, ITerm>>> p_eqs = paramsPattern.asTerm(Optional::get);
            try {
                if(!diseqs.unify(p_eqs._2()).isPresent()) {
                    return Stream.empty();
                }
            } catch(OccursException e) {
                return Stream.empty();
            }
    
            // Add disunifications for all patterns from previous rules
            final boolean guardsOk = guards.stream().allMatch(g -> {
                final IRenaming swap = fresh.fresh(g.getVars());
                final Pattern g1 = g.eliminateWld(() -> fresh.fresh("_"));
                final Tuple2<ITerm, List<Tuple2<ITermVar, ITerm>>> t_eqs = g1.apply(swap).asTerm(Optional::get);
                // Add internal equalities from the guard pattern, which are also reasons why the guard wouldn't match
                final List<ITermVar> leftEqs =
                        t_eqs._2().stream().map(Tuple2::_1).collect(ImmutableList.toImmutableList());
                final List<ITerm> rightEqs =
                        t_eqs._2().stream().map(Tuple2::_2).collect(ImmutableList.toImmutableList());
                final ITerm left = B.newTuple(p_eqs._1(), B.newTuple(leftEqs));
                final ITerm right = B.newTuple(t_eqs._1(), B.newTuple(rightEqs));
                final java.util.Set<ITermVar> universals = fresh.reset();
                return diseqs.disunify(universals, left, right).isPresent();
            });
            if(!guardsOk)
                return Stream.empty();
    
            // Add params as guard for next rule
            guards.add(paramsPattern);
    
            final IConstraint body = Constraints.conjoin(StateUtil.asInequalities(diseqs), r.body());
            return Stream.of(r.withParams(paramPatterns).withBody(body));
        }).collect(ImmutableSet.toImmutableSet());
    }
    */

    /**
     * Inline rule into the i-th matching premise of the second rule. Inlining always succeeds (use simplify to solve
     * equalities in the resulting rule). The function returns empty if nothing was inlined because no i-th matching
     * premise existed.
     */
    public static Optional<Rule> inline(Rule rule, int ith, Rule into) {
        final FreshVars fresh = new FreshVars(vars(into));

        final AtomicInteger i = new AtomicInteger(0);
        final CExists newBody = (CExists) Constraints.map(c -> {
            if(!(c instanceof CUser)) {
                return c;
            }
            final CUser constraint = (CUser) c;
            if(!constraint.name().equals(rule.name())) {
                return c;
            }
            if(i.getAndIncrement() != ith) {
                return c;
            }

            return applyToConstraint(fresh, rule, constraint.args());
        }, false).apply(into.body());

        if(i.get() <= ith) {
            // nothing was inlined
            return Optional.empty();
        }

        return Optional.of(into.withLabel("").withBody(newBody));
    }

    private static IConstraint applyToConstraint(FreshVars fresh, Rule rule, List<? extends ITerm> args) {
        final IRenaming swap = fresh.fresh(rule.paramVars());
        final Pattern rulePatterns = P.newTuple(rule.params()).eliminateWld(() -> fresh.fresh("_"));
        final Tuple2<ITerm, List<Tuple2<ITermVar, ITerm>>> p_eqs = rulePatterns.asTerm(v -> v.get());
        final ITerm p = swap.apply(p_eqs._1());
        final ITerm t = B.newTuple(args);
        final CEqual eq = new CEqual(t, p);
        final Set.Immutable<ITermVar> newVars = fresh.reset();
        final IConstraint newConstraint = Constraints.exists(newVars, new CConj(eq, rule.body().apply(swap)));
        return newConstraint;
    }

    /**
     * Simplify the rule by hoisting existentials to the top and solving (dis)equalities. Returns empty if the
     * (dis)equalities are inconsistent, otherwise return the simplified rule.
     */
    public static Rule simplify(Rule rule) {
        return equalitiesAsUnifier(rule);
    }

    /**
     * Optimize rules for fast application by inlining head patterns and pre-unifiying equalities.
     */
    public static Rule optimizeRule(Rule rule) {
        rule = headPatternsAsBodyEqualities(rule);
        rule = equalitiesAsUnifier(rule);
        rule = instantiateHeadPatterns(rule);
        return rule;
    }


    /**
     * Transform rule such that all non-variable head patterns are eliminated and added as equality constraints to the
     * rule body.
     * 
     * Head patterns are not preserved, but eliminated.
     * 
     * For example:
     * 
     * <pre>
     *   { Id(x) :- x == y }  --->  { arg :- arg == Id(x), x == y }
     * </pre>
     */
    public static Rule headPatternsAsBodyEqualities(Rule rule) {
        return rule;
        /*
        final Set.Immutable<ITermVar> freeVars = freeVars(rule);
        
        // 1. make arguments
        final FreshVars fresh = new FreshVars(freeVars);
        final VarProvider freshProvider = VarProvider.of(fresh::fresh, () -> fresh.fresh("_"));
        final List<ITerm> args = rule.params().stream().map(p -> fresh.fresh("arg")).collect(Collectors.toList());
        final Set.Immutable<ITermVar> argVars = fresh.fix();
        
        // 2. match patterns
        //    match patterns against abstract arguments to get substitution and implied equalities
        final MatchResult matchResult;
        if((matchResult = P.matchWithEqs(rule.params(), args, PersistentUniDisunifier.Immutable.of(), freshProvider)
                .orElse(null)) == null) {
            return Rule.of(rule.name(),
                    rule.params().stream().map(p -> P.newWld()).collect(ImmutableList.toImmutableList()),
                    RuleBody.of(new CFalse()));
        }
        final Set.Immutable<ITermVar> matchVars = fresh.fix();
        
        // 3. update unifier with eqs
        final IUniDisunifier.Immutable matchUnifier;
        try {
            if((matchUnifier =
                    rule.unifier().unify(matchResult.equalities()).map(r -> r.unifier()).orElse(null)) == null) {
                return Rule.of(rule.name(),
                        rule.params().stream().map(p -> P.newWld()).collect(ImmutableList.toImmutableList()),
                        Set.Immutable.of(), PersistentUniDisunifier.Immutable.of(), new CFalse());
            }
        } catch(OccursException ex) {
            return Rule.of(rule.name(),
                    rule.params().stream().map(p -> P.newWld()).collect(ImmutableList.toImmutableList()),
                    Set.Immutable.of(), PersistentUniDisunifier.Immutable.of(), new CFalse());
        }
        
        // 4. construct body
        final IConstraint matchBody = rule.body().apply(matchResult.substitution());
        final Set.Immutable<ITermVar> freeMatchBodyVars = freeVars(matchVars, matchUnifier, matchBody);
        
        // 5. construct params
        final List<Pattern> matchParams = args.stream().map(t -> P.fromTerm(t, v -> !freeMatchBodyVars.contains(v)))
                .collect(ImmutableList.toImmutableList());
        
        return Rule.of(rule.name(), matchParams, matchVars, matchUnifier, matchBody);
        */
    }

    /**
     * Transform rule such that equalities in the body constraint are removed from the body constraint and incorporated
     * in the unifier.
     * 
     * Head patterns are preserved.
     * 
     * For example:
     * 
     * <pre>
     *   { x :- x == Id(y) }  --->  { x :- x == Id(x) | true }
     * </pre>
     */
    public static Rule equalitiesAsUnifier(Rule rule) {
        return rule;
    }

    /**
     * Transform rule such that head patterns are maximally instantiated based on the unifier.
     * 
     * Head patterns are not preserved, but may only become more specific.
     */
    public static Rule instantiateHeadPatterns(Rule rule) {
        return rule;
        /*
        final Set.Immutable<ITermVar> freeVars = freeVars(rule);
        
        final FreshVars fresh = new FreshVars(Set.Immutable.union( //
                freeVars, // prevent capture of free variables
                rule.evars() // prevent capture by evars as we move under existential
        ));
        
        // convert patterns to terms, creating variables and adding equalities from non-linear patterns
        final List<ITerm> params = new ArrayList<>();
        final IUniDisunifier.Transient unifier = rule.unifier().melt();
        boolean okay = rule.params().stream().allMatch(p -> {
            final Tuple2<ITerm, List<Tuple2<ITermVar, ITerm>>> termResult =
                    p.asTerm(v -> v.map(fresh::fresh).orElseGet(() -> fresh.fresh("_")));
            params.add(termResult._1());
            try {
                return unifier.unify(termResult._2()).isPresent();
            } catch(OccursException e) {
                return false;
            }
        });
        if(!okay) {
            return Rule.of(rule.name(), rule.params(), RuleBody.of(new CFalse()));
        }
        Set.Immutable<ITermVar> paramVars = fresh.fix();
        
        // inline unifier into patterns
        for(int i = 0; i < params.size(); i++) {
            params.set(i, unifier.findRecursive(params.get(i)));
        }
        
        // remove original free vars from patterns
        paramVars = params.stream().flatMap(t -> t.getVars().stream()).collect(CapsuleCollectors.toSet());
        final IRenaming surrogates = fresh.fresh(Set.Immutable.intersect(freeVars, paramVars));
        for(int i = 0; i < params.size(); i++) {
            params.set(i, surrogates.apply(params.get(i)));
        }
        try {
            if(!unifier.unify(surrogates.entrySet()).isPresent()) {
                throw new IllegalStateException("Unexpected failure.");
            }
        } catch(OccursException ex) {
            throw new IllegalStateException("Unexpected failure.");
        }
        
        // cleanup unifier
        paramVars = params.stream().flatMap(t -> t.getVars().stream()).collect(CapsuleCollectors.toSet());
        final Set.Immutable<ITermVar> bodyVars = Constraints.freeVars(rule.body());
        final Set.Immutable<ITermVar> retainedVars = freeVars.__insertAll(paramVars).__insertAll(bodyVars);
        unifier.retainAll(retainedVars);
        
        // cleanup evars
        final Set.Immutable<ITermVar> evars = Set.Immutable.intersect(Set.Immutable.subtract(rule.evars(), paramVars),
                Set.Immutable.union(unifier.varSet(), bodyVars));
        final Set.Immutable<ITermVar> newFreeVars = freeVars(evars, unifier, rule.body());
        
        final List<Pattern> patterns = params.stream().map(t -> P.fromTerm(t, v -> !newFreeVars.contains(v)))
                .collect(ImmutableList.toImmutableList());
        return Rule.of(rule.name(), patterns, evars, unifier.freeze(), rule.body());
        */
    }


    /**
     * Make closed fragments from the given rules by inlining into the given rules. The predicates includePredicate and
     * includeRule determine which premises should be inlined. The fragments are closed only w.r.t. the included
     * predicates.
     */
    public static SetMultimap<String, Rule> makeFragments(RuleSet rules, Predicate1<String> includePredicate,
            Predicate2<String, String> includeRule, int generations) {
        final SetMultimap<String, Rule> fragments = HashMultimap.create();

        // 1. make all rules unordered, and keep included rules
        final SetMultimap<String, Rule> newRules = HashMultimap.create();
        for(String ruleName : rules.getRuleNames()) {
            if(includePredicate.test(ruleName)) {
                for(Rule r : rules.getOrderIndependentRules(ruleName)) {
                    if(includeRule.test(r.name(), r.label())) {
                        newRules.put(ruleName, r);
                    }
                }
            }
        }

        final PartialFunction1<IConstraint, CUser> expandable =
                c -> (c instanceof CUser && newRules.containsKey(((CUser) c).name())) ? Optional.of(((CUser) c))
                        : Optional.empty();

        // 2. find the included axioms and move to fragments
        for(Map.Entry<String, Rule> e : newRules.entries()) {
            if(Constraints.collectBase(expandable, false).apply(e.getValue().body()).isEmpty()) {
                fragments.put(e.getKey(), e.getValue());
            }
        }
        fragments.forEach(newRules::remove);

        // 3. for each generation, inline fragments into rules
        for(int g = 0; g < generations; g++) {
            final SetMultimap<String, Rule> generation = HashMultimap.create();
            for(Map.Entry<String, Rule> e : newRules.entries()) {
                final String name = e.getKey();
                final Rule r = e.getValue();
                final FreshVars fresh = new FreshVars(vars(r));
                final List<IConstraint> cs = Constraints.flatMap(c -> {
                    final Optional<CUser> u = expandable.apply(c);
                    if(u.isPresent()) {
                        return fragments.get(u.get().name()).stream()
                                .map(f -> applyToConstraint(fresh, f, u.get().args()));
                    } else {
                        return Stream.of(c);
                    }
                }, false).apply(r.body()).collect(Collectors.toList());
                for(IConstraint c : cs) {
                    final Rule f = r.withLabel("").withBody(new CExists(CapsuleUtil.immutableSet(), c));
                    generation.put(name, simplify(f));
                }
            }
            fragments.putAll(generation);
        }

        return ImmutableSetMultimap.copyOf(fragments);
    }


    public static Set.Immutable<ITermVar> vars(Rule rule) {
        final Set.Transient<ITermVar> vars = CapsuleUtil.transientSet();
        vars(rule, vars::__insert);
        return vars.freeze();
    }

    public static void vars(Rule rule, Action1<ITermVar> onVar) {
        rule.paramVars().forEach(onVar::apply);
        Constraints.vars(rule.body(), onVar);
    }

}