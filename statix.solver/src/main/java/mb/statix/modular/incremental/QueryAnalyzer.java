package mb.statix.modular.incremental;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import mb.statix.constraints.CResolveQuery;
import mb.statix.modular.dependencies.details.QueryDependencyDetail;
import mb.statix.modular.incremental.changeset.IChangeSet;
import mb.statix.modular.incremental.manager.QueryIncrementalManager;
import mb.statix.modular.module.IModule;
import mb.statix.modular.solver.Context;
import mb.statix.modular.solver.state.IMState;
import mb.statix.solver.IConstraint;

public class QueryAnalyzer {
    private IChangeSet changeSet;
    private Map<IModule, Set<IConstraint>> leftConstraints = new HashMap<>();
    
    
    public void phase1() {
        Set<IModule> dirty = changeSet.dirty();
        
        //Now we need to solve dirty modules again.
        for (IModule module : dirty) {
            Context.context().addModule(module);
            
            //Add the initialization constraint as incomplete
            IMState state = module.getCurrentState();
            state.solver().getCompleteness().add(module.getInitialization(), state.unifier());
        }
        
        
        
    }
    
    public void checkQueries(IModule module) {
        //TODO We need to know based on the
        
        Flag flag = module.getTopFlag();
        if (flag.getCleanliness().isCleanish()) {
            //This module is clean.
            switchToClean(module);
        }
        
        IMState state = module.getCurrentState();
        
        //TODO This code needs to run after the solver has been created, but before the runner can become "stuck".
        //TODO IMPORTANT In other words, not here.
        for (QueryDependencyDetail qdd : module.queries().keySet()) {
            CResolveQuery query = qdd.getOriginalConstraint();
            state.solver().getStore().add(query);
        }
        //The given module should be a clirty one. We now have to redo it's queries
        //TrackingNameResolution<Scope, ITerm, ITerm> nameResolution = TrackingNameResolution.builder();
    }
    
    public void switchToClean(IModule module) {
        //Set as clean
        module.setFlag(Flag.CLEAN);
        
        //Allow module access
        Context.context().<QueryIncrementalManager>getIncrementalManager().allowAccess(module.getId());
        
        //Resolve pending incompleteness
        IMState state = module.getCurrentState();
        Set<IConstraint> pending = state.solver().getStore().getAllRemainingConstraints();
        //The pending set should be empty
        assert pending.isEmpty() : "" + module + ": I expect the set of pending constraints to be empty when a module is marked as clean, otherwise why did we add the constraints in the first place?";
        
        //the incompleteness should only be filled with these constraints, so we don't actually need to resolve the completeness if it is empty?
        state.solver().getCompleteness().removeAll(pending, state.unifier());
        
        //We do have to remove the initialization to make it complete
        state.solver().getCompleteness().remove(module.getInitialization(), state.unifier());
    }
}