package lama.mariadb.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.mariadb.MariaDBSchema;
import lama.mariadb.MariaDBSchema.MariaDBDataType;
import lama.mariadb.MariaDBSchema.MariaDBTable.MariaDBEngine;
import lama.mariadb.ast.MariaDBVisitor;
import lama.sqlite3.gen.SQLite3Common;

public class MariaDBTableGenerator {

	private final StringBuilder sb = new StringBuilder();
	private String tableName;
	private MariaDBSchema s;
	private PrimaryKeyState primaryKeyState = Randomly.fromOptions(PrimaryKeyState.values());
	private final List<String> columnNames = new ArrayList<>();
	private final Randomly r;
	private final List<String> errors = new ArrayList<>();

	public MariaDBTableGenerator(String tableName, Randomly r, MariaDBSchema newSchema) {
		this.tableName = tableName;
		this.s = newSchema;
		this.r = r;
	}

	public static Query generate(String tableName, Randomly r, MariaDBSchema newSchema) {
		return new MariaDBTableGenerator(tableName, r, newSchema).gen();
	}

	private Query gen() {
		if (Randomly.getBoolean() || s.getDatabaseTables().isEmpty()) {
			newTable();
		} else {
			likeOtherTable();
		}
		return new QueryAdapter(sb.toString(), errors);
	}
	
	private enum PrimaryKeyState {
		NO_PRIMARY_KEY,
		COLUMN_CONSTRAINT,
		TABLE_CONSTRAINT
	}

	private void newTable() {
		createOrReplaceTable();
		sb.append("(");
		for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			String columnName = SQLite3Common.createColumnName(i);
			columnNames.add(columnName);
			sb.append(columnName);
			sb.append(" ");
			MariaDBDataType dataType = Randomly.fromOptions(MariaDBDataType.values());
			switch (dataType) {
			case INT:
				sb.append(Randomly.fromOptions("SMALLINT", "MEDIUMINT", "INT", "BIGINT"));
				addSignedness();
				break;
			case VARCHAR:
				sb.append(Randomly.fromOptions("VARCHAR(100)", "CHAR(100)"));
				break;
			case REAL:
				sb.append("REAL");
				addSignedness();
				break;
			case BOOLEAN:
				sb.append("BOOLEAN");
				break;
			}
			final boolean isGeneratedColumn;
			if (Randomly.getBoolean() && false) { // wait for crash bug to be fixed
				sb.append(" GENERATED ALWAYS AS(");
				// TODO columns
				sb.append(MariaDBVisitor.asString(new MariaDBExpressionGenerator(r).getRandomExpression()));
				sb.append(")");
				isGeneratedColumn = true;
			} else {
				isGeneratedColumn = false;
			}
			sb.append(" ");
			if (Randomly.getBoolean() && !isGeneratedColumn) {
				sb.append(" UNIQUE");
			}
			if (Randomly.getBoolean() && primaryKeyState == PrimaryKeyState.COLUMN_CONSTRAINT && !isGeneratedColumn) {
				sb.append(" PRIMARY KEY");
				primaryKeyState = PrimaryKeyState.NO_PRIMARY_KEY;
			}
			if (Randomly.getBoolean()  && !isGeneratedColumn) {
				sb.append(" NOT NULL");
			}
		}
		if (primaryKeyState == PrimaryKeyState.TABLE_CONSTRAINT) {
			sb.append(", PRIMARY KEY(");
			sb.append(Randomly.nonEmptySubset(columnNames).stream().collect(Collectors.joining(", ")));
			sb.append(")");
			errors.add("Primary key cannot be defined upon a generated column");
			
		}
		sb.append(")");
		if (Randomly.getBoolean()) {
			sb.append(" engine=");
			sb.append(MariaDBEngine.getRandomEngine().getTextRepresentation());
		}
	}

	private void addSignedness() {
		if (Randomly.getBoolean()) {
			sb.append(" ");
			sb.append(Randomly.fromOptions("SIGNED", "UNSIGNED", "ZEROFILL"));
		}
	}

	private void likeOtherTable() {
		createOrReplaceTable();
		sb.append(" LIKE ");
		sb.append(s.getRandomTable().getName());
	}

	private void createOrReplaceTable() {
		sb.append("CREATE ");
		boolean replace = false;
		if (Randomly.getBoolean()) {
			sb.append("OR REPLACE ");
			replace = true;
		}
		// TODO temporary
//		if (Randomly.getBoolean()) {
//			sb.append("TEMPORARY ");
//		}
		sb.append("TABLE ");
		if (Randomly.getBoolean() && !replace) {
			sb.append("IF NOT EXISTS ");
		}
		sb.append(tableName);
	}

}