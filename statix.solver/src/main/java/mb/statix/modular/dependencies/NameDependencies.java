package mb.statix.modular.dependencies;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map.Entry;
import java.util.Set;

import mb.nabl2.terms.ITerm;
import mb.nabl2.util.collections.HashTrieRelation3;
import mb.nabl2.util.collections.IRelation3;
import mb.statix.modular.dependencies.details.IDependencyDetail;
import mb.statix.modular.dependencies.details.NameDependencyDetail;
import mb.statix.modular.dependencies.details.QueryDependencyDetail;
import mb.statix.modular.name.Name;
import mb.statix.modular.name.NameAndRelation;
import mb.statix.scopegraph.terms.Scope;

public class NameDependencies extends Dependencies {
    private static final long serialVersionUID = 1L;
    
    /**
     * A mapping from (name + relation) -> scope -> dependency.
     * The owner of this dependencies object depends on the names in this table because of the
     * dependencies in this table.
     */
    private transient IRelation3.Transient<NameAndRelation, Scope, Dependency> table =
            HashTrieRelation3.Transient.of();

    public NameDependencies(String owner) {
        super(owner);
    }

    /**
     * @param name
     *      the name and the relation
     * 
     * @return
     *      per scope the dependency of this module that is affected by the given name and relation
     */
    public Set<? extends Entry<Scope, Dependency>> getNameDependencies(NameAndRelation name) {
        return table.get(name);
    }
    
    /**
     * Convenience method.
     * 
     * @param name
     *      the name
     * @param relation
     *      the relation
     * 
     * @return
     *      per scope the dependency of this module that is affected by the given name and relation
     */
    public final Set<? extends Entry<Scope, Dependency>> getNameDependencies(Name name, ITerm relation) {
        return getNameDependencies(name.withRelation(relation));
    }
    
    /**
     * @param name
     *      the name and the relation
     * @param scope
     *      the scope
     * 
     * @return
     *      the dependencies of this module that are affected by the given name, relation and scope
     */
    public Set<Dependency> getNameDependencies(NameAndRelation name, Scope scope) {
        return table.get(name, scope);
    }
    
    /**
     * Convenience method.
     * 
     * @param name
     *      the name
     * @param relation
     *      the relation
     * @param scope
     *      the scope
     * 
     * @return
     *      the dependencies of this module that are affected by the given name, relation and scope
     */
    public final Set<Dependency> getNameDependencies(Name name, ITerm relation, Scope scope) {
        return getNameDependencies(name.withRelation(relation), scope);
    }
    
    /**
     * @return
     *      the dependencies of this module that depend on names 
     */
    public Set<Dependency> values() {
        return table.valueSet();
    }
    
    /**
     * @return
     *      the names that this module depends on
     */
    public Set<NameAndRelation> names() {
        return table.keySet();
    }
    
    @Override
    public Dependency addDependency(String module, IDependencyDetail... details) {
        Dependency dependency = super.addDependency(module, details);
        
        NameDependencyDetail ndetail = dependency.getDetails(NameDependencyDetail.class);
        QueryDependencyDetail qdetail = dependency.getDetails(QueryDependencyDetail.class);
        final NameAndRelation nar = ndetail.toNameAndRelation();
        
        //TODO maybe we don't want to do this for every data scope, but rather find a different
        //     way of storing things
        for (Scope scope : qdetail.getDataScopes()) {
            table.put(nar, scope, dependency);
        }
        
        return dependency;
    }
    
    @Override
    public NameDependencies copy() {
        NameDependencies copy = new NameDependencies(owner);
        copy.dependencies.putAll(dependencies);
        copy.dependants.putAll(dependants);
        copy.table.putAll(table);
        return copy;
    }
    
    // --------------------------------------------------------------------------------------------
    // Serialization
    // --------------------------------------------------------------------------------------------
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        IRelation3.Immutable<NameAndRelation, Scope, Dependency> frozen = table.freeze();
        out.writeObject(frozen);
        table = frozen.melt();
    }
    
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        IRelation3.Immutable<NameAndRelation, Scope, Dependency> frozen =
                (IRelation3.Immutable<NameAndRelation, Scope, Dependency>) in.readObject();
        table = frozen.melt();
    }
}