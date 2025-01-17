package mb.statix.constraints.messages;

import java.util.Optional;

import org.metaborg.util.functions.Action1;
import org.metaborg.util.functions.Function0;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.IRenaming;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.util.TermFormatter;

public interface IMessage {

    MessageKind kind();

    String toString(TermFormatter formatter, Function0<String> getDefaultMessage);

    Optional<ITerm> origin();

    void visitVars(Action1<ITermVar> onVar);

    IMessage apply(ISubstitution.Immutable subst);

    IMessage apply(IRenaming subst);

}