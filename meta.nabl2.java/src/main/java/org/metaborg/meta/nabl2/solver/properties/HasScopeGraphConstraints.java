package org.metaborg.meta.nabl2.solver.properties;

import org.metaborg.meta.nabl2.constraints.IConstraint;

public class HasScopeGraphConstraints implements IConstraintSetProperty {

    private int i = 0;

    @Override public boolean add(IConstraint constraint) {
        return constraint.match(IConstraint.Cases.of(
            // @formatter:off
            c -> false,
            c -> false,
            c -> false,
            c -> {
                i++;
                return true;
            },
            c -> false,
            c -> false,
            c -> false,
            c -> false,
            c -> false
            // @formatter:on
        ));
    }

    @Override public boolean remove(IConstraint constraint) {
        return constraint.match(IConstraint.Cases.of(
            // @formatter:off
            c -> false,
            c -> false,
            c -> false,
            c -> {
                i--;
                assert i >= 0 : "Removed more constraints than were added.";
                return true;
            },
            c -> false,
            c -> false,
            c -> false,
            c -> false,
            c -> false
            // @formatter:on
        ));
    }

    public boolean isEmpty() {
        return i == 0;
    }

}