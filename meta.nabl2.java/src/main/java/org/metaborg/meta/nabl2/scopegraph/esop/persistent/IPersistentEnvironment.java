package org.metaborg.meta.nabl2.scopegraph.esop.persistent;

import java.io.Serializable;
import java.util.Optional;

import org.metaborg.meta.nabl2.scopegraph.ILabel;
import org.metaborg.meta.nabl2.scopegraph.IOccurrence;
import org.metaborg.meta.nabl2.scopegraph.IScope;
import org.metaborg.meta.nabl2.scopegraph.path.IDeclPath;
import org.metaborg.meta.nabl2.scopegraph.path.IPath;

import io.usethesource.capsule.Set;

public interface IPersistentEnvironment<S extends IScope, L extends ILabel, O extends IOccurrence, P extends IPath<S, L, O>>
        extends Serializable {

    /**
     * Return a set of resolved paths if the associated scope graph contains
     * enough information to perform the resolution. If not resolvable at the
     * current point in time a call to this method results in
     * {@link Optional#empty()}.
     * 
     * @return a set of resolved paths, or {@link Optional#empty()} if not (yet)
     *         resolvable.
     */
    Optional<Set.Immutable<P>> solution();

    interface Filter<S extends IScope, L extends ILabel, O extends IOccurrence, P extends IPath<S, L, O>>
            extends FilterPredicate<S, L, O, P>, Serializable {

        Object matchToken(P p);

        boolean shortCircuit();

    }

    @FunctionalInterface
    interface FilterPredicate<S extends IScope, L extends ILabel, O extends IOccurrence, P extends IPath<S, L, O>> {

        Optional<P> test(IDeclPath<S, L, O> path);

    }

}