package mb.statix.constraints;

import java.io.Serializable;
import java.util.Optional;

import javax.annotation.Nullable;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.substitution.IRenaming;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.util.TermFormatter;
import mb.statix.solver.IConstraint;

public class CNew implements IConstraint, Serializable {
    private static final long serialVersionUID = 1L;

    private final ITerm scopeTerm;
    private final ITerm datumTerm;

    private final @Nullable IConstraint cause;

    public CNew(ITerm scopeTerm, ITerm datumTerm) {
        this(scopeTerm, datumTerm, null);
    }

    public CNew(ITerm scopeTerm, ITerm datumTerm, @Nullable IConstraint cause) {
        this.scopeTerm = scopeTerm;
        this.datumTerm = datumTerm;
        this.cause = cause;
    }

    public ITerm scopeTerm() {
        return scopeTerm;
    }

    public ITerm datumTerm() {
        return datumTerm;
    }

    @Override public <R> R match(Cases<R> cases) {
        return cases.caseNew(this);
    }

    @Override public <R, E extends Throwable> R matchOrThrow(CheckedCases<R, E> cases) throws E {
        return cases.caseNew(this);
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CNew withCause(@Nullable IConstraint cause) {
        return new CNew(scopeTerm, datumTerm, cause);
    }

    @Override public CNew apply(ISubstitution.Immutable subst) {
        return new CNew(subst.apply(scopeTerm), subst.apply(datumTerm), cause);
    }

    @Override public CNew apply(IRenaming subst) {
        return new CNew(subst.apply(scopeTerm), subst.apply(datumTerm), cause);
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("new ");
        sb.append(termToString.format(scopeTerm));
        sb.append(" : ");
        sb.append(termToString.format(datumTerm));
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}