package org.metaborg.meta.nabl2.spoofax.primitives;

import java.util.List;
import java.util.Optional;

import org.metaborg.meta.nabl2.scopegraph.terms.Occurrence;
import org.metaborg.meta.nabl2.spoofax.IScopeGraphContext;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.generic.TermIndex;
import org.spoofax.interpreter.core.InterpreterException;

public class SG_get_decl_scope extends ScopeGraphPrimitive {

    public SG_get_decl_scope() {
        super(SG_get_decl_scope.class.getSimpleName(), 0, 1);
    }

    @Override public Optional<ITerm> call(IScopeGraphContext<?> context, ITerm term, List<ITerm> terms)
            throws InterpreterException {
        if (terms.size() != 1) {
            throw new InterpreterException("Need one term argument: analysis");
        }
        TermIndex index = terms.get(0).getAttachments().getInstance(TermIndex.class);
        if (index == null) {
            return Optional.empty();
        }
        return Occurrence.matcher().match(term).<ITerm> flatMap(decl -> {
            return context.unit(index.getResource()).solution().<ITerm> flatMap(s -> {
                return s.getScopeGraph().getDeclScope(decl).flatMap(Optional::<ITerm> of);
            });
        });
    }

}