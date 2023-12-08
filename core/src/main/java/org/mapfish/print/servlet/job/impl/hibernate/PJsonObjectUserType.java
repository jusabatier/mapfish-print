package org.mapfish.print.servlet.job.impl.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.json.JSONException;
import org.json.JSONObject;
import org.mapfish.print.wrapper.json.PJsonObject;

/** Hibernate User Type for PJson object. */
public class PJsonObjectUserType implements UserType<Object> {

  private static final String CONTEXT_NAME = "spec";

  @Override
  public final Object assemble(final Serializable cached, final Object owner) {
    return deepCopy(cached);
  }

  @Override
  public final Object deepCopy(final Object value) {
    if (value == null) {
      return value;
    } else {
      try {
        return new PJsonObject(
            new JSONObject(((PJsonObject) value).getInternalObj().toString()), CONTEXT_NAME);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public final Serializable disassemble(final Object value) {
    return (Serializable) deepCopy(value);
  }

  @Override
  public final boolean equals(final Object x, final Object y) {
    if (x == null) {
      return (y != null);
    } else {
      return (x.equals(y));
    }
  }

  @Override
  public final int hashCode(final Object x) {
    return x.hashCode();
  }

  @Override
  public final boolean isMutable() {
    return false;
  }

  @Override
  public Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
          throws SQLException {
    String value = rs.getString(position);
    if (value != null) {
      try {
        return new PJsonObject(new JSONObject(value), CONTEXT_NAME);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  @Override
  public final void nullSafeSet(
      final PreparedStatement st,
      final Object value,
      final int index,
      final SharedSessionContractImplementor session)
      throws SQLException {
    if (value == null) {
      st.setNull(index, Types.LONGVARCHAR);
    } else {
      st.setString(index, ((PJsonObject) value).getInternalObj().toString());
    }
  }

  @Override
  public final Object replace(final Object original, final Object target, final Object owner) {
    return deepCopy(original);
  }

  @Override
  public final Class<Object> returnedClass() {
    return Object.class;
  }

  @Override
  public int getSqlType() {
    return Types.LONGVARCHAR;
  }
}
