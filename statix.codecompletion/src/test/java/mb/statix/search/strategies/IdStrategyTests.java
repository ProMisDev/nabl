package mb.statix.search.strategies;

import mb.statix.search.Strategy;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;


/**
 * Tests the {@link IdStrategy} class.
 */
public final class IdStrategyTests {

    @Test
    public void returnsInputUnchanged() throws InterruptedException {
        // Arrange
        String input = "a";
        IdStrategy<String, Object> str = new IdStrategy<>();

        // Act
        Stream<String> outputStream = str.apply(null, input);
        List<String> outputs = outputStream.collect(Collectors.toList());

        // Assert
        List<String> expected = Collections.singletonList(input);
        assertEquals(expected, outputs);
    }

}
