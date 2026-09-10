package ch.exmachina.cosmo42.utils;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.IteratorUtils.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

public abstract class Iterables {
    private Iterables() {
    }

    /**
     * Returns the source {@link Iterable} as {@link RandomAccess} {@link List} view
     * over it.
     * 
     * Note that if the source is already a random access list, it will be the value
     * returned, otherwise the source will be copied in random access list in
     * iteration order.
     * 
     * @param <E>
     * @param <C>
     * @param source
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <E, C extends List<E> & RandomAccess> C asRandomAccess(Iterable<E> source) {
        if (source instanceof RandomAccess && source instanceof List<E> result) {
            return (C) result;
        }
        return (C) stream(source).collect(toCollection(ArrayList::new));
    }
}
