package mb.statix.solver.constraint;

import static mb.nabl2.terms.build.TermBuild.B;

import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import mb.nabl2.scopegraph.terms.Scope;
import mb.nabl2.stratego.TermIndex;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.util.TermFormatter;
import mb.statix.solver.ConstraintContext;
import mb.statix.solver.ConstraintResult;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.State;
import mb.statix.spoofax.StatixTerms;

public class CTermId implements IConstraint {

    private final ITerm term;
    private final ITerm idTerm;

    private final @Nullable IConstraint cause;

    public CTermId(ITerm term, ITerm idTerm) {
        this(term, idTerm, null);
    }

    public CTermId(ITerm term, ITerm idTerm, @Nullable IConstraint cause) {
        this.term = term;
        this.idTerm = idTerm;
        this.cause = cause;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CTermId withCause(@Nullable IConstraint cause) {
        return new CTermId(term, idTerm, cause);
    }

    @Override public CTermId apply(ISubstitution.Immutable subst) {
        return new CTermId(subst.apply(term), subst.apply(idTerm), cause);
    }

    @Override public Optional<ConstraintResult> solve(State state, ConstraintContext params) throws Delay {
        final IUnifier unifier = state.unifier();
        if(!(unifier.isGround(term))) {
            throw Delay.ofVars(unifier.getVars(term));
        }
        final CEqual eq;
        final Optional<Scope> maybeScope = Scope.matcher().match(term, unifier);
        if(maybeScope.isPresent()) {
            final Scope scope = maybeScope.get();
            eq = new CEqual(idTerm, B.newAppl(StatixTerms.SCOPEID_OP, scope.getArgs()));
        } else {
            final Optional<TermIndex> maybeIndex = TermIndex.get(unifier.findTerm(term));
            if(maybeIndex.isPresent()) {
                final TermIndex index = maybeIndex.get();
                eq = new CEqual(idTerm, B.newAppl(StatixTerms.TERMID_OP, index.getArgs()));
            } else {
                eq = new CEqual(idTerm, B.newAppl(StatixTerms.NOID_OP));
            }
        }
        return Optional.of(ConstraintResult.of(state, ImmutableSet.of(eq)));
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("termId(");
        sb.append(termToString.format(term));
        sb.append(", ");
        sb.append(termToString.format(idTerm));
        sb.append(")");
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}