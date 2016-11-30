package io.bootique.jdbc.test;

import java.util.ArrayList;
import java.util.List;

/**
 * @since 0.13
 */
public class InsertBuilder {

    private DatabaseChannel channel;
    private String tableName;
    private List<Column> columns;
    private List<List<Binding>> bindings;

    public InsertBuilder(DatabaseChannel channel, String tableName, List<Column> columns) {
        this.channel = channel;
        this.bindings = new ArrayList<>();
        this.tableName = tableName;
        this.columns = columns;
    }

    public InsertBuilder values(Object... values) {
        if (columns.size() != values.length) {
            throw new IllegalArgumentException(tableName + ": values do not match columns. There are " + columns.size()
                    + " column(s) " + "and " + values.length + " value(s).");
        }

        List<Binding> bindings = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            Column col = columns.get(i);
            bindings.add(new Binding(col, values[i]));
        }

        this.bindings.add(bindings);
        return this;
    }

    public void exec() {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(channel.quote(tableName)).append(" (");

        for (int i = 0; i < columns.size(); i++) {

            Column col = columns.get(i);

            if (i > 0) {
                sql.append(", ");
            }

            sql.append(channel.quote(col.getName()));
        }

        sql.append(") VALUES ");

        List<Binding> flatBindings = new ArrayList<>();

        for (int i = 0; i < bindings.size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("(");

            List<Binding> rowBindings = bindings.get(i);

            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) {
                    sql.append(", ");
                }

                sql.append("?");
                flatBindings.add(rowBindings.get(j));
            }

            sql.append(")");
        }

        channel.update(sql.toString(), flatBindings);
    }

}
