package com.google.common.collect;

/** Guava stub: only the members the mod calls. */
public abstract class ImmutableTable<R, C, V> implements Table<R, C, V> {
    public static <R, C, V> Builder<R, C, V> builder() { return new Builder<R, C, V>(); }

    @Override
    public abstract ImmutableMap<C, V> row(R rowKey);
    @Override
    public abstract ImmutableMap<R, V> column(C columnKey);
    @Override
    public abstract ImmutableSet<Table.Cell<R, C, V>> cellSet();
    @Override
    public abstract ImmutableSet<R> rowKeySet();
    @Override
    public abstract ImmutableSet<C> columnKeySet();
    @Override
    public abstract ImmutableCollection<V> values();
    @Override
    public abstract ImmutableMap<R, java.util.Map<C, V>> rowMap();
    @Override
    public abstract ImmutableMap<C, java.util.Map<R, V>> columnMap();

    public static final class Builder<R, C, V> {
        public Builder<R, C, V> put(R rowKey, C columnKey, V value) { return this; }
        public ImmutableTable<R, C, V> build() { return null; }
    }
}
