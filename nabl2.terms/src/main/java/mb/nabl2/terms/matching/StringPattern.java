package mb.nabl2.terms.matching;

import static mb.nabl2.terms.build.TermBuild.B;

import java.util.Objects;
import java.util.Optional;

import org.metaborg.util.collection.CapsuleUtil;
import org.metaborg.util.functions.Action2;
import org.metaborg.util.functions.Function0;
import org.metaborg.util.functions.Function1;

import io.usethesource.capsule.Set;
import mb.nabl2.terms.IAttachments;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.Terms;
import mb.nabl2.terms.substitution.IRenaming;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.u.IUnifier;

class StringPattern extends Pattern {
    private static final long serialVersionUID = 1L;

    private final String value;

    public StringPattern(String value, IAttachments attachments) {
        super(attachments);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override public Set<ITermVar> getVars() {
        return CapsuleUtil.immutableSet();
    }

    @Override public boolean isConstructed() {
        return true;
    }

    @Override protected boolean matchTerm(ITerm term, ISubstitution.Transient subst, IUnifier.Immutable unifier,
            Eqs eqs) {
        // @formatter:off
        return unifier.findTerm(term).match(Terms.<Boolean>cases()
            .string(stringTerm -> {
                return stringTerm.getValue().equals(value);
            }).var(v -> {
                eqs.add(v, this);
                return true;
            }).otherwise(t -> {
                return false;
            })
        );
        // @formatter:on
    }

    @Override public StringPattern apply(IRenaming subst) {
        return this;
    }

    @Override public StringPattern eliminateWld(Function0<ITermVar> fresh) {
        return this;
    }

    @Override protected ITerm asTerm(Action2<ITermVar, ITerm> equalities,
            Function1<Optional<ITermVar>, ITermVar> fresh) {
        return B.newString(value, getAttachments());
    }

    @Override public String toString() {
        return "\"" + value + "\"";
    }

    @Override public boolean equals(Object o) {
        if(this == o)
            return true;
        if(o == null || getClass() != o.getClass())
            return false;
        StringPattern that = (StringPattern) o;
        return Objects.equals(value, that.value);
    }

    @Override public int hashCode() {
        return Objects.hash(value);
    }
}
