package org.metaborg.meta.nabl2.scopegraph.esop.persistent;

import static org.metaborg.meta.nabl2.scopegraph.esop.persistent.CollectionConverter.liftHashFunctionToRelation;
import static org.metaborg.meta.nabl2.scopegraph.esop.persistent.CollectionConverter.union;

import java.util.Objects;

import org.metaborg.meta.nabl2.scopegraph.ILabel;
import org.metaborg.meta.nabl2.scopegraph.IOccurrence;
import org.metaborg.meta.nabl2.scopegraph.IResolutionParameters;
import org.metaborg.meta.nabl2.scopegraph.IScope;
import org.metaborg.meta.nabl2.scopegraph.OpenCounter;
import org.metaborg.meta.nabl2.scopegraph.esop.IEsopScopeGraph;
import org.metaborg.meta.nabl2.scopegraph.terms.Label;
import org.metaborg.meta.nabl2.util.collections.HashFunction;
import org.metaborg.meta.nabl2.util.collections.HashRelation3;
import org.metaborg.meta.nabl2.util.collections.IFunction;
import org.metaborg.meta.nabl2.util.collections.IInverseFunction;
import org.metaborg.meta.nabl2.util.collections.IRelation3;
import org.metaborg.meta.nabl2.util.functions.Function1;
import org.metaborg.meta.nabl2.util.tuples.Tuple3;

import io.usethesource.capsule.BinaryRelation;
import io.usethesource.capsule.BinaryRelation.Immutable;
import io.usethesource.capsule.Set;
import io.usethesource.capsule.util.stream.CapsuleCollectors;

public class PersistentScopeGraph<S extends IScope, L extends ILabel, O extends IOccurrence>
        implements IEsopScopeGraph<S, L, O>, java.io.Serializable {

    private static final long serialVersionUID = 42L;

    private final Set.Immutable<S> allScopes;

    protected final IRelation3<S, L, S> directEdges;

    protected final IRelation3<S, L, O> declarations;
    protected final IRelation3<S, L, O> references;

    // TODO
    // private final TernaryRelation.Immutable<S, L, S> directEdges;
    // private final TernaryRelation.Immutable<S, L, O> declarations;
    // private final TernaryRelation.Immutable<S, L, O> references;

    public PersistentScopeGraph(final Set.Immutable<S> allScopes, final Set.Immutable<O> allDeclarations,
            final Set.Immutable<O> allReferences, final IFunction<O, S> declarations, final IFunction<O, S> references,
            final IRelation3<S, L, S> directEdges, final IRelation3<O, L, S> exportEdges,
            final IRelation3<S, L, O> importEdges) {
        this.allScopes = allScopes;
              
        this.directEdges = directEdges;

        this.declarations = union(exportEdges.inverse(), liftHashFunctionToRelation(declarations, (L) Label.D).inverse());
        this.references = union(importEdges, liftHashFunctionToRelation(references, (L) Label.R).inverse());
    }

    @Override
    public Set.Immutable<S> getAllScopes() {
        return allScopes;
    }

    @Override
    public Set.Immutable<O> getAllDecls() {
        // projection of third column
        return declarations.stream().map(Tuple3::_3).collect(CapsuleCollectors.toSet());
    }

    @Override
    public Set.Immutable<O> getAllRefs() {
        // projection of third column        
        return references.stream().map(Tuple3::_3).collect(CapsuleCollectors.toSet());
    }

    @Deprecated
    @Override
    public IFunction<O, S> getDecls() {
        final IFunction.Mutable<O, S> result = HashFunction.create();
        
        // filter and project
        declarations.stream().filter(tuple -> tuple._2().equals(Label.D)).iterator()
                .forEachRemaining(tuple -> result.put(tuple._3(), tuple._1()));

        return result;
    }

    @Deprecated
    @Override
    public IFunction<O, S> getRefs() {
        final IFunction.Mutable<O, S> result = HashFunction.create();
                
        // filter and project        
        references.stream().filter(tuple -> tuple._2().equals(Label.R)).iterator()
                .forEachRemaining(tuple -> result.put(tuple._3(), tuple._1()));
        
        return result;
    }

    @Override
    public IRelation3<S, L, S> getDirectEdges() {
        return directEdges;
    }

    @Override
    public IRelation3<O, L, S> getExportEdges() {
        final IRelation3.Mutable<S, L, O> result = HashRelation3.create();

        // filter
        declarations.stream().filter(tuple -> !tuple._2().equals(Label.D)).iterator()
                .forEachRemaining(tuple -> result.put(tuple._1(), tuple._2(), tuple._3()));

        return result.inverse();
    }

    @Override
    public IRelation3<S, L, O> getImportEdges() {
        final IRelation3.Mutable<S, L, O> result = HashRelation3.create();

        // filter
        references.stream().filter(tuple -> !tuple._2().equals(Label.R)).iterator()
                .forEachRemaining(tuple -> result.put(tuple._1(), tuple._2(), tuple._3()));

        return result;
    }

    @Override
    public PersistentNameResolution<S, L, O> resolve(IResolutionParameters<L> params, OpenCounter<S, L> scopeCounter, Function1<S, String> tracer) {
        return new PersistentNameResolution<>(this, params, scopeCounter);
    }

    public static class Builder<S extends IScope, L extends ILabel, O extends IOccurrence>
            implements IEsopScopeGraph.Builder<S, L, O> {

        private final Set.Transient<S> allScopes;
        private final Set.Transient<O> allDeclarations;
        private final Set.Transient<O> allReferences;

        private final IFunction.Mutable<O, S> declarations;
        private final IFunction.Mutable<O, S> references;

        private final IRelation3.Mutable<S, L, S> directEdges;
        private final IRelation3.Mutable<O, L, S> exportEdges;
        private final IRelation3.Mutable<S, L, O> importEdges;
       
        private IEsopScopeGraph<S, L, O> result = null;

        public Builder() {
            this.allScopes = Set.Transient.of();
            this.allDeclarations = Set.Transient.of();
            this.allReferences = Set.Transient.of();

            this.declarations = HashFunction.create();
            this.references = HashFunction.create();

            this.directEdges = HashRelation3.create();
            this.exportEdges = HashRelation3.create();
            this.importEdges = HashRelation3.create();
        }

        void requireNonSealed() {
            if (result != null) {
                throw new IllegalStateException("Mutation prohibited, builder is already closed.");
            }
        }
        
        void requireNonEqual(Object one, Object two) {
            if (Objects.equals(one, two)) {
                throw new IllegalArgumentException(String.format("Arguments must not equal:\n\t%s\n\t%s", one, two));
            }
        }
        
        public void addDirectEdge(S sourceScope, L label, S targetScope) {
            requireNonSealed();

            allScopes.__insert(sourceScope);
            allScopes.__insert(targetScope);
            directEdges.put(sourceScope, label, targetScope);
        }
        
        public void addDecl(S scope, O decl) {
            requireNonSealed();            
            
            allScopes.__insert(scope);
            allDeclarations.__insert(decl);
            declarations.put(decl, scope);
        }
        
        public void addAssoc(O decl, L label, S scope) {
            requireNonSealed();
            requireNonEqual(Label.P, label);
            
            allScopes.__insert(scope);
            allDeclarations.__insert(decl);
            exportEdges.put(decl, label, scope);
        }      
        
        public void addRef(O ref, S scope) {
            requireNonSealed();
            
            allScopes.__insert(scope);
            allReferences.__insert(ref);
            references.put(ref, scope);
        }

        public void addImport(S scope, L label, O ref) {
            requireNonSealed();
            requireNonEqual(Label.R, label);
            
            allScopes.__insert(scope);
            allReferences.__insert(ref);
            importEdges.put(scope, label, ref);
        }

        @Override
        public Set<S> getAllScopes() {
            return allScopes;
        }

        @Override
        public Set<O> getAllDecls() {
            return allDeclarations;
        }

        @Override
        public Set<O> getAllRefs() {
            return allReferences;
        }

        @Override
        public IFunction<O, S> getDecls() {
            return declarations;
        }

        @Override
        public IFunction<O, S> getRefs() {
            return references;
        }

        @Override
        public IRelation3<S, L, S> getDirectEdges() {
            return directEdges;
        }

        @Override
        public IRelation3<O, L, S> getExportEdges() {
            return exportEdges;
        }

        @Override
        public IRelation3<S, L, O> getImportEdges() {
            return importEdges;
        }

        @Override
        public IEsopScopeGraph<S, L, O> result() {
            if (result == null) {
                result = new PersistentScopeGraph<>(allScopes.freeze(), allDeclarations.freeze(),
                        allReferences.freeze(), declarations, references, directEdges, exportEdges, importEdges);
            }

            return result;
        }
    }
}

class CollectionConverter {

    // TODO: release Capsule and change input type to BinaryRelation.Immutable
    public static final <T, U> IFunction<T, U> relationToHashFunction(BinaryRelation<T, U> input) {
        final IFunction.Mutable<T, U> output = HashFunction.create();
        input.entryIterator().forEachRemaining(entry -> output.put(entry.getKey(), entry.getValue()));
        return output;
    }
    
    public static final <T, U> BinaryRelation.Immutable<T, U> hashFunctionToRelation(IInverseFunction<T, U> input) {
        return (Immutable<T, U>) hashFunctionToRelation(input.inverse()).inverse();
    }
    
    public static final <T, U> BinaryRelation.Immutable<T, U> hashFunctionToRelation(IFunction<T, U> input) {
        final BinaryRelation.Transient<T, U> output = BinaryRelation.Transient.of();
        input.keySet().forEach(key -> output.__insert(key, input.get(key).get()));
        return output.freeze();
    }

    public static final <T, U, V> IRelation3<T, U, V> liftHashFunctionToRelation(IInverseFunction<T, V> input,
            U intermediate) {
        return liftHashFunctionToRelation(input.inverse(), intermediate).inverse();
    }
    
    public static final <T, U, V> IRelation3<T, U, V> liftHashFunctionToRelation(IFunction<T, V> input,
            U intermediate) {
        final IRelation3.Mutable<T, U, V> output = HashRelation3.create();
        input.keySet().forEach(key -> output.put(key, intermediate, input.get(key).get()));
        return output;
    }
    
    public static final <T, U, V> IRelation3<T, U, V> union(IRelation3<T, U, V> one, IRelation3<T, U, V> two) {
        final IRelation3.Mutable<T, U, V> result = HashRelation3.create();

        one.stream().iterator().forEachRemaining(tuple -> result.put(tuple._1(), tuple._2(), tuple._3()));
        two.stream().iterator().forEachRemaining(tuple -> result.put(tuple._1(), tuple._2(), tuple._3()));

        return result;
    }
    
}