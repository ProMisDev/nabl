package mb.nabl2.terms.build;

import java.util.Objects;

import org.immutables.value.Value;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultiset;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;

public abstract class AbstractTermVar extends AbstractTerm implements ITermVar {

    @Value.Parameter @Override public abstract String getResource();

    @Value.Parameter @Override public abstract String getName();

    @Value.Check protected void check() {
        Preconditions.checkState(!(getResource().isEmpty() && getName().isEmpty()),
                "'resource' and 'name' cannot both be empty");
    }

    @Override public boolean isGround() {
        return false;
    }

    @Value.Lazy @Override public ImmutableMultiset<ITermVar> getVars() {
        return ImmutableMultiset.of(this);
    }

    @Override public <T> T match(ITerm.Cases<T> cases) {
        return cases.caseVar(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(ITerm.CheckedCases<T, E> cases) throws E {
        return cases.caseVar(this);
    }

    @Value.Lazy @Override public int hashCode() {
        return Objects.hash(getResource(), getName());
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ITermVar)) return false;
        ITermVar that = (ITermVar)other;
        if (this.hashCode() != that.hashCode()) return false;
        // @formatter:off
        return Objects.equals(getResource(), that.getResource()) &&
               Objects.equals(getName(), that.getName());
        // @formatter:on
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("?");
        if(!getResource().isEmpty()) {
            sb.append(getResource());
            sb.append("-");
        }
        sb.append(getName());
        return sb.toString();
    }

}