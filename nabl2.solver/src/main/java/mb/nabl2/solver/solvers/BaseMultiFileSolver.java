package mb.nabl2.solver.solvers;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.metaborg.util.Ref;
import org.metaborg.util.functions.Function1;
import org.metaborg.util.functions.Predicate1;
import org.metaborg.util.functions.Predicate2;
import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;

import io.usethesource.capsule.Set;
import mb.nabl2.config.NaBL2DebugConfig;
import mb.nabl2.constraints.IConstraint;
import mb.nabl2.relations.variants.IVariantRelation;
import mb.nabl2.relations.variants.VariantRelations;
import mb.nabl2.scopegraph.ScopeGraphReducer;
import mb.nabl2.scopegraph.esop.IEsopNameResolution;
import mb.nabl2.scopegraph.esop.IEsopScopeGraph;
import mb.nabl2.scopegraph.esop.lazy.EsopNameResolution;
import mb.nabl2.scopegraph.terms.Label;
import mb.nabl2.scopegraph.terms.Occurrence;
import mb.nabl2.scopegraph.terms.Scope;
import mb.nabl2.solver.ISolution;
import mb.nabl2.solver.ISolver;
import mb.nabl2.solver.ISolver.SolveResult;
import mb.nabl2.solver.ImmutableSolution;
import mb.nabl2.solver.SolverConfig;
import mb.nabl2.solver.SolverCore;
import mb.nabl2.solver.components.BaseComponent;
import mb.nabl2.solver.components.EqualityComponent;
import mb.nabl2.solver.components.NameResolutionComponent;
import mb.nabl2.solver.components.NameResolutionComponent.NameResolutionResult;
import mb.nabl2.solver.components.NameSetsComponent;
import mb.nabl2.solver.components.RelationComponent;
import mb.nabl2.solver.components.SetComponent;
import mb.nabl2.solver.components.SymbolicComponent;
import mb.nabl2.solver.exceptions.DelayException;
import mb.nabl2.solver.exceptions.SolverException;
import mb.nabl2.solver.messages.IMessages;
import mb.nabl2.symbolic.ISymbolicConstraints;
import mb.nabl2.symbolic.SymbolicConstraints;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.unification.u.IUnifier;
import mb.nabl2.util.collections.Properties;

public class BaseMultiFileSolver extends BaseSolver {

    public BaseMultiFileSolver(NaBL2DebugConfig nabl2Debug, CallExternal callExternal) {
        super(nabl2Debug, callExternal);
    }

    public ISolution solveIntra(GraphSolution initial, Collection<ITermVar> intfVars,
            @Nullable Collection<Scope> intfScopes, Function1<String, String> fresh, ICancel cancel, IProgress progress)
            throws SolverException, InterruptedException {
        final SolverConfig config = initial.config();

        // shared
        final Ref<IUnifier.Immutable> unifier = new Ref<>(initial.unifier());

        // guards -- intfScopes == null indicates we do not know the interface scopes, and resolution should be delayed.
        final Predicate2<Scope, Label> isEdgeClosed = (s, l) -> intfScopes != null && !intfScopes.contains(s);

        // more shared
        final IEsopScopeGraph.Transient<Scope, Label, Occurrence, ITerm> scopeGraph = initial.scopeGraph().melt();
        final IEsopNameResolution<Scope, Label, Occurrence> nameResolution =
                EsopNameResolution.of(config.getResolutionParams(), scopeGraph, isEdgeClosed);
        final ScopeGraphReducer scopeGraphReducer = new ScopeGraphReducer(scopeGraph, unifier);

        // solver components
        final SolverCore core = new SolverCore(config, unifier, fresh, callExternal);
        final BaseComponent baseSolver = new BaseComponent(core);
        final EqualityComponent equalitySolver = new EqualityComponent(core, unifier);
        final NameResolutionComponent nameResolutionSolver =
                new NameResolutionComponent(core, scopeGraph, nameResolution, Properties.Transient.of());
        final NameSetsComponent nameSetSolver = new NameSetsComponent(core, nameResolution);
        final RelationComponent relationSolver = new RelationComponent(core, Predicate1.never(), config.getFunctions(),
                VariantRelations.transientOf(config.getRelations()));
        final SetComponent setSolver = new SetComponent(core, nameSetSolver.nameSets());
        final SymbolicComponent symSolver = new SymbolicComponent(core, SymbolicConstraints.of());

        // @formatter:off
        final ISolver component = c -> c.matchOrThrow(IConstraint.CheckedCases.<SolveResult, DelayException>builder()
                    .onBase(baseSolver::solve)
                    .onEquality(equalitySolver::solve)
                    .onNameResolution(nameResolutionSolver::solve)
                    .onRelation(relationSolver::solve)
                    .onSet(setSolver::solve)
                    .onSym(symSolver::solve)
                    .otherwise(ISolver.defer())
        );
        // @formatter:on
        final FixedPointSolver solver = new FixedPointSolver(cancel, progress, component);

        solver.step().subscribe(r -> {
            Set.Immutable<ITermVar> vars = r.result.unifierDiff().varSet();
            if(!vars.isEmpty()) {
                try {
                    r.resolveCriticalEdges(scopeGraphReducer.update(vars));
                } catch(InterruptedException ex) {
                    // ignore here
                }
            }
        });

        try {
            scopeGraphReducer.updateAll();
            final SolveResult solveResult = solver.solve(initial.constraints(), unifier);

            NameResolutionResult nameResolutionResult = nameResolutionSolver.finish();
            IUnifier.Immutable unifierResult = equalitySolver.finish();
            Map<String, IVariantRelation.Immutable<ITerm>> relationResult = relationSolver.finish();
            ISymbolicConstraints symbolicConstraints = symSolver.finish();
            setSolver.finish();

            final IMessages.Transient messages = initial.messages().melt();
            messages.addAll(solveResult.messages());

            return ImmutableSolution
                    .of(config, initial.astProperties(), nameResolutionResult.scopeGraph(),
                            nameResolutionResult.declProperties(), relationResult, unifierResult, symbolicConstraints,
                            messages.freeze(), solveResult.constraints())
                    .withNameResolutionCache(nameResolutionResult.resolutionCache());
        } catch(RuntimeException ex) {
            throw new SolverException("Internal solver error.", ex);
        }

    }

}
