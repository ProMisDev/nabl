package mb.nabl2.terms.unification.ud;

import java.io.Serializable;
import java.util.Map.Entry;
import java.util.Optional;

import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.Sets;

import io.usethesource.capsule.Map;
import io.usethesource.capsule.Set;
import io.usethesource.capsule.util.stream.CapsuleCollectors;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.OccursException;
import mb.nabl2.terms.unification.u.IUnifier;
import mb.nabl2.terms.unification.u.PersistentUnifier;

public abstract class PersistentUniDisunifier extends BaseUniDisunifier implements Serializable {

    private static final long serialVersionUID = 42L;

    protected static ITermVar findRep(ITermVar var, Map.Transient<ITermVar, ITermVar> reps) {
        ITermVar rep = reps.get(var);
        if(rep == null) {
            return var;
        } else {
            rep = findRep(rep, reps);
            reps.__put(var, rep);
            return rep;
        }
    }

    ///////////////////////////////////////////
    // class Immutable
    ///////////////////////////////////////////

    public static class Immutable extends PersistentUniDisunifier implements IUniDisunifier.Immutable, Serializable {

        private static final long serialVersionUID = 42L;

        private final IUnifier.Immutable unifier;
        private final Set.Immutable<Diseq> disequalities;

        Immutable(final boolean finite, final Map.Immutable<ITermVar, ITermVar> reps,
                final Map.Immutable<ITermVar, Integer> ranks, final Map.Immutable<ITermVar, ITerm> terms,
                Set.Immutable<Diseq> disequalities) {
            this(new PersistentUnifier.Immutable(finite, reps, ranks, terms), disequalities);
        }

        Immutable(final IUnifier.Immutable unifier, Set.Immutable<Diseq> disequalities) {
            this.unifier = unifier;
            this.disequalities = disequalities;
        }

        @Override public boolean isFinite() {
            return unifier.isFinite();
        }

        @Override protected IUnifier.Immutable unifier() {
            return unifier;
        }

        @Override public Set.Immutable<Diseq> disequalities() {
            return disequalities;
        }

        @Override public ITermVar findRep(ITermVar var) {
            return unifier.findRep(var);
        }

        ///////////////////////////////////////////
        // unify(ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<IUniDisunifier.Result<IUnifier.Immutable>> unify(ITerm left, ITerm right)
                throws OccursException {
            return unifier.unify(left, right).flatMap(r -> {
                return disunifyAll(r.unifier(), disequalities).map(diseqs -> {
                    final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), diseqs);
                    return new ImmutableResult<>(r.result(), ud);
                });
            });
        }

        @Override public Optional<IUniDisunifier.Result<IUnifier.Immutable>>
                unify(Iterable<? extends Entry<? extends ITerm, ? extends ITerm>> equalities) throws OccursException {
            return unifier.unify(equalities).flatMap(r -> {
                return disunifyAll(r.unifier(), disequalities).map(diseqs -> {
                    final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), diseqs);
                    return new ImmutableResult<>(r.result(), ud);
                });
            });
        }

        @Override public Optional<IUniDisunifier.Result<IUnifier.Immutable>>
                unify(mb.nabl2.terms.unification.u.IUnifier other) throws OccursException {
            return unifier.unify(other).flatMap(r -> {
                return disunifyAll(r.unifier(), disequalities).map(diseqs -> {
                    final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), diseqs);
                    return new ImmutableResult<>(r.result(), ud);
                });
            });
        }

        @Override public Optional<IUniDisunifier.Result<IUnifier.Immutable>> unify(IUniDisunifier other)
                throws OccursException {
            return unifier.unify(other.equalityMap().entrySet()).flatMap(r -> {
                return disunifyAll(r.unifier(), disequalities).map(diseqs -> {
                    final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), diseqs);
                    return new ImmutableResult<>(r.result(), ud);
                });
            });
        }

        ///////////////////////////////////////////
        // diff(ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<IUnifier.Immutable> diff(ITerm term1, ITerm term2) {
            // FIXME Do we need to include disequalities in this result?
            try {
                return unify(term1, term2).map(IUniDisunifier.Result::result);
            } catch(OccursException e) {
                return Optional.empty();
            }
        }

        ///////////////////////////////////////////
        // disunify(Set<ITermVar>, ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<IUniDisunifier.Immutable> disunify(Iterable<ITermVar> universals, ITerm left,
                ITerm right) {
            final IUnifier.Transient diseqs = PersistentUnifier.Immutable.of(isFinite()).melt();
            try {
                if(!diseqs.unify(left, right).isPresent()) {
                    return Optional.of(this);
                }
            } catch(OccursException e) {
                return Optional.of(this);
            }
            final Diseq diseq = new Diseq(universals, diseqs.freeze());

            final Set.Immutable<Diseq> disequalities;
            if((disequalities = disunifyAll(unifier, this.disequalities.__insert(diseq)).orElse(null)) == null) {
                return Optional.empty();
            }

            final IUniDisunifier.Immutable newUnifier = new PersistentUniDisunifier.Immutable(unifier, disequalities);
            return Optional.of(newUnifier);
        }

        /**
         * Simplify disequalities for an updated unifier
         */
        private static Optional<Set.Immutable<Diseq>> disunifyAll(IUnifier.Immutable unifier,
                Set.Immutable<Diseq> disequalities) {
            final Set.Transient<Diseq> newDisequalities = Set.Transient.of();

            // reduce 
            for(Diseq diseq : disequalities) {
                final Optional<IUnifier.Immutable> result = disunify(unifier, diseq.disequalities());
                if(!result.isPresent()) {
                    // disequality discharged, terms are unequal
                    continue;
                }

                final IUnifier.Immutable newDiseq = result.get().removeAll(diseq.universals()).unifier();
                if(newDiseq.isEmpty()) {
                    // no disequalities left, terms are equal
                    return Optional.empty();
                }

                final Set.Immutable<ITermVar> universals = diseq.universals().stream()
                        .flatMap(v -> unifier.getVars(v).stream()).collect(CapsuleCollectors.toSet());
                final java.util.Set<ITermVar> universalVars = Sets.intersection(universals, newDiseq.freeVarSet());

                // not unified yet, keep
                newDisequalities.__insert(new Diseq(universalVars, newDiseq));
            }

            removeImpliedDisequalities(unifier, newDisequalities);

            return Optional.of(newDisequalities.freeze());
        }

        private static void removeImpliedDisequalities(IUnifier.Immutable unifier, Set.Transient<Diseq> disequalities) {
            for(Diseq diseq : disequalities) {
                for(Diseq otherDiseq : disequalities) {
                    // check entailment
                }
            }
        }

        /**
         * Disunify the given disequality.
         * 
         * Reduces the disequality to canonical form for the current unifier. Returns a reduced map of disequalities, or
         * none if the disequality is satisfied.
         */
        private static Optional<IUnifier.Immutable> disunify(IUnifier.Immutable unifier, IUnifier.Immutable diseq) {
            final Optional<? extends IUnifier.Result<? extends IUnifier.Immutable>> unifyResult;
            try {
                unifyResult = unifier.unify(diseq);
            } catch(OccursException e) {
                // unify failed, terms are unequal
                return Optional.empty();
            }
            if(!unifyResult.isPresent()) {
                // unify failed, terms are unequal
                return Optional.empty();
            }
            // unify succeeded, terms are not unequal
            final IUnifier.Immutable diff = unifyResult.get().result();
            return Optional.of(diff);
        }

        ///////////////////////////////////////////
        // retain(ITermVar)
        ///////////////////////////////////////////

        @Override public IUniDisunifier.Result<ISubstitution.Immutable> retain(ITermVar var) {
            return retainAll(Iterables2.singleton(var));
        }

        @Override public IUniDisunifier.Result<ISubstitution.Immutable> retainAll(Iterable<ITermVar> vars) {
            final IUnifier.Result<ISubstitution.Immutable> r = unifier.retainAll(vars);
            // FIXME Update disequalities
            final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), disequalities);
            return new ImmutableResult<>(r.result(), ud);
        }

        ///////////////////////////////////////////
        // remove(ITermVar)
        ///////////////////////////////////////////

        @Override public IUniDisunifier.Result<ISubstitution.Immutable> remove(ITermVar var) {
            return removeAll(Iterables2.singleton(var));
        }

        @Override public IUniDisunifier.Result<ISubstitution.Immutable> removeAll(Iterable<ITermVar> vars) {
            final IUnifier.Result<ISubstitution.Immutable> r = unifier.removeAll(vars);
            // FIXME Update disequalities
            final IUniDisunifier.Immutable ud = new PersistentUniDisunifier.Immutable(r.unifier(), disequalities);
            return new ImmutableResult<>(r.result(), ud);
        }


        ///////////////////////////////////////////
        // construction
        ///////////////////////////////////////////

        @Override public IUniDisunifier.Transient melt() {
            return new BaseUniDisunifier.Transient(this);
        }

        public static IUniDisunifier.Immutable of() {
            return of(true);
        }

        public static IUniDisunifier.Immutable of(boolean finite) {
            return new PersistentUniDisunifier.Immutable(PersistentUnifier.Immutable.of(finite), Set.Immutable.of());
        }

    }

}