package mb.statix.constraints;

import java.io.Serializable;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.Nullable;

import mb.nabl2.relations.IRelation;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.util.TermFormatter;
import mb.statix.modular.solver.MConstraintContext;
import mb.statix.modular.solver.MConstraintResult;
import mb.statix.modular.solver.state.IMState;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.spoofax.StatixTerms;

/**
 * Implementation for the pathlt constraint.
 * 
 * <pre>pathLt[priorities](label1, label2)</pre>
 */
public class CPathLt implements IConstraint, Serializable {
    private static final long serialVersionUID = 1L;

    private final IRelation.Immutable<ITerm> lt;
    private final ITerm label1Term;
    private final ITerm label2Term;

    private final @Nullable IConstraint cause;

    /**
     * Creates a new pathLt constraint without a cause.
     * 
     * @param lt
     *      the priorities
     * @param l1
     *      the first label term (variable)
     * @param l2
     *      the second label term (variable)
     */
    public CPathLt(IRelation.Immutable<ITerm> lt, ITerm l1, ITerm l2) {
        this(lt, l1, l2, null);
    }

    /**
     * Creates a new pathLt constraint with the given cause.
     * 
     * @param lt
     *      the priorities
     * @param l1
     *      the first label term (variable)
     * @param l2
     *      the second label term (variable)
     * @param cause
     *      the cause of this constraint
     */
    public CPathLt(IRelation.Immutable<ITerm> lt, ITerm l1, ITerm l2, @Nullable IConstraint cause) {
        this.lt = lt;
        this.label1Term = l1;
        this.label2Term = l2;
        this.cause = cause;
    }

    public IRelation.Immutable<ITerm> lt() {
        return lt;
    }

    public ITerm label1Term() {
        return label1Term;
    }

    public ITerm label2Term() {
        return label2Term;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CPathLt withCause(@Nullable IConstraint cause) {
        return new CPathLt(lt, label1Term, label2Term, cause);
    }

    @Override public <R> R match(Cases<R> cases) {
        return cases.casePathLt(this);
    }

    @Override
    public Optional<MConstraintResult> solve(IMState state, MConstraintContext params) throws Delay {
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
            return Optional.of(new MConstraintResult());
        } else {
            return Optional.empty();
        }
    }

    @Override public <R, E extends Throwable> R matchOrThrow(CheckedCases<R, E> cases) throws E {
        return cases.casePathLt(this);
    }

    @Override public CPathLt apply(ISubstitution.Immutable subst) {
        return new CPathLt(lt, subst.apply(label1Term), subst.apply(label2Term), cause);
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("pathLt[");
        sb.append(lt);
        sb.append("](");
        sb.append(termToString.format(label1Term));
        sb.append(", ");
        sb.append(termToString.format(label2Term));
        sb.append(")");
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}