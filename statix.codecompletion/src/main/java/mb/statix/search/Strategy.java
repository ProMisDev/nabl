package mb.statix.search;

import mb.statix.sequences.Sequence;


/**
 * A strategy, which takes an input and produces a list of outputs.
 */
@FunctionalInterface
public interface Strategy<I, O, CTX> {

    /**
     * Applies the search strategy.
     *
     * @param ctx the search context
     * @param input the input
     * @return a sequence of results; or an empty sequence when the strategy failed
     * @throws InterruptedException the operation was interrupted
     */
    Sequence<O> apply(CTX ctx, I input) throws InterruptedException;

}