package org.metaborg.meta.nabl2.regexp;

import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Version(value = 42L)
abstract class Complement<S> implements IRegExp<S> {

    @Value.Parameter public abstract IRegExp<S> getRE();

    @Value.Parameter public abstract IRegExpBuilder<S> getBuilder();

    @Value.Lazy @Override public boolean isNullable() {
        return !getRE().isNullable();
    }

    @Override public <T> T match(IRegExpCases<S,T> visitor) {
        return visitor.complement(getRE());
    }

    @Override public String toString() {
        return "~(" + getRE() + ")";
    }

}
