package org.metaborg.meta.nabl2.scopegraph.esop;

import org.metaborg.meta.nabl2.scopegraph.ILabel;
import org.metaborg.meta.nabl2.scopegraph.IOccurrence;
import org.metaborg.meta.nabl2.scopegraph.IResolutionParameters;
import org.metaborg.meta.nabl2.scopegraph.IScope;
import org.metaborg.meta.nabl2.scopegraph.IScopeGraph;
import org.metaborg.meta.nabl2.scopegraph.esop.reference.EsopScopeGraph;
import org.metaborg.meta.nabl2.util.collections.IRelation3;
import org.metaborg.meta.nabl2.util.functions.PartialFunction1;
import org.metaborg.meta.nabl2.util.functions.Predicate2;

import com.google.common.annotations.Beta;

import io.usethesource.capsule.SetMultimap;

@Beta
public interface IEsopScopeGraph<S extends IScope, L extends ILabel, O extends IOccurrence, V>
        extends IScopeGraph<S, L, O> {

    public static final boolean USE_PERSISTENT_SCOPE_GRAPH = Boolean.getBoolean("usePersistentScopeGraph");

    /*
     * Factory method to switch between different scope graph implementations.
     */
    static <S extends IScope, L extends ILabel, O extends IOccurrence, V> IEsopScopeGraph.Transient<S, L, O, V>
            builder() {
        if(USE_PERSISTENT_SCOPE_GRAPH) {
            throw new IllegalArgumentException("Persisent scope graphs are temporarily removed.");
        } else {
            return EsopScopeGraph.Transient.of();
        }
    }

    boolean isOpen(S scope, L label);

    IRelation3<S, L, V> incompleteDirectEdges();

    IRelation3<S, L, V> incompleteImportEdges();

    boolean isComplete();

    IEsopNameResolution<S, L, O> resolve(IResolutionParameters<L> params, Predicate2<S, L> isEdgeClosed);

    interface Immutable<S extends IScope, L extends ILabel, O extends IOccurrence, V>
            extends IEsopScopeGraph<S, L, O, V>, IScopeGraph.Immutable<S, L, O> {

        IRelation3.Immutable<S, L, V> incompleteDirectEdges();

        IRelation3.Immutable<S, L, V> incompleteImportEdges();

        SetMultimap.Immutable<S, L> openEdges();

        IEsopScopeGraph.Transient<S, L, O, V> melt();

    }

    interface Transient<S extends IScope, L extends ILabel, O extends IOccurrence, V>
            extends IEsopScopeGraph<S, L, O, V> {

        boolean addOpen(S scope, L label);

        boolean removeOpen(S scope, L label);

        boolean addDecl(S scope, O decl);

        boolean addRef(O ref, S scope);

        boolean addDirectEdge(S sourceScope, L label, S targetScope);

        boolean addIncompleteDirectEdge(S scope, L label, V var);

        boolean addAssoc(O decl, L label, S scope);

        boolean addImport(S scope, L label, O ref);

        boolean addIncompleteImportEdge(S scope, L label, V var);

        boolean addAll(IEsopScopeGraph<S, L, O, V> other);

        boolean reduce(PartialFunction1<V, S> fs, PartialFunction1<V, O> fo);

        // -----------------------

        IEsopScopeGraph.Immutable<S, L, O, V> freeze();

    }

}