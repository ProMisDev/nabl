package mb.statix.solver.constraint;

import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import mb.nabl2.relations.IRelation;
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

public class CPathLt implements IConstraint {

    private final IRelation.Immutable<ITerm> lt;
    private final ITerm label1Term;
    private final ITerm label2Term;

    private final @Nullable IConstraint cause;

    public CPathLt(IRelation.Immutable<ITerm> lt, ITerm l1, ITerm l2) {
        this(lt, l1, l2, null);
    }

    public CPathLt(IRelation.Immutable<ITerm> lt, ITerm l1, ITerm l2, @Nullable IConstraint cause) {
        this.lt = lt;
        this.label1Term = l1;
        this.label2Term = l2;
        this.cause = cause;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CPathLt withCause(@Nullable IConstraint cause) {
        return new CPathLt(lt, label1Term, label2Term, cause);
    }

    @Override public CPathLt apply(ISubstitution.Immutable subst) {
        return new CPathLt(lt, subst.apply(label1Term), subst.apply(label2Term), cause);
    }

    @Override public Optional<ConstraintResult> solve(State state, ConstraintContext params) throws Delay {
        final IUnifier unifier = state.unifier();
        if(!(unifier.isGround(label1Term))) {
            throw Delay.ofVars(unifier.getVars(label1Term));
        }
        if(!(unifier.isGround(label2Term))) {
            throw Delay.ofVars(unifier.getVars(label2Term));
        }
        final ITerm label1 = StatixTerms.label().match(label1Term, unifier)
                .orElseThrow(() -> new IllegalArgumentException("Expected label, got " + unifier.toString(label1Term)));
        final ITerm label2 = StatixTerms.label().match(label2Term, unifier)
                .orElseThrow(() -> new IllegalArgumentException("Expected label, got " + unifier.toString(label2Term)));
        if(lt.contains(label1, label2)) {
            return Optional.of(ConstraintResult.of(state, ImmutableSet.of()));
        } else {
            return Optional.empty();
        }


    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("pathLt[");
        sb.append(lt);
        sb.append("](");
        sb.append(termToString.apply(label1Term));
        sb.append(", ");
        sb.append(termToString.apply(label2Term));
        sb.append(")");
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}