package org.metaborg.meta.nabl2.constraints.scopegraph;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.constraints.IConstraint;
import org.metaborg.meta.nabl2.constraints.messages.IMessageContent;
import org.metaborg.meta.nabl2.constraints.messages.IMessageInfo;
import org.metaborg.meta.nabl2.constraints.messages.MessageContent;
import org.metaborg.meta.nabl2.scopegraph.terms.Label;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermVar;

import io.usethesource.capsule.Set;

@Value.Immutable
@Serial.Version(value = 42L)
public abstract class CGDirectEdge implements IScopeGraphConstraint {

    @Value.Parameter public abstract ITerm getSourceScope();

    @Value.Parameter public abstract Label getLabel();

    @Value.Parameter public abstract ITerm getTargetScope();

    @Override public Set.Immutable<ITermVar> getVars() {
        return getSourceScope().getVars().__insertAll(getTargetScope().getVars());
    }

    @Value.Parameter @Override public abstract IMessageInfo getMessageInfo();

    @Override public <T> T match(Cases<T> cases) {
        return cases.caseDirectEdge(this);
    }

    @Override public <T> T match(IConstraint.Cases<T> cases) {
        return cases.caseScopeGraph(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(CheckedCases<T, E> cases) throws E {
        return cases.caseDirectEdge(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(IConstraint.CheckedCases<T, E> cases) throws E {
        return cases.caseScopeGraph(this);
    }

    @Override public IMessageContent pp() {
        return MessageContent.builder().append(getSourceScope()).append(" -" + getLabel().getName() + "-> ")
                .append(getTargetScope()).build();
    }

    @Override public String toString() {
        return pp().toString();
    }

}