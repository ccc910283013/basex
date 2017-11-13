package org.basex.query.value.type;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;
import org.basex.util.*;

/**
 * Type for maps.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Leo Woerteler
 */
public final class MapType extends FuncType {
  /**
   * Constructor.
   * @param type argument type
   * @param declType declared return type
   */
  MapType(final AtomType type, final SeqType declType) {
    super(declType, type.seqType());
  }

  @Override
  public byte[] string() {
    return Token.token(MAP);
  }

  @Override
  public Map cast(final Item item, final QueryContext qc, final StaticContext sc,
      final InputInfo info) throws QueryException {

    if(item instanceof Map) {
      final Map m = (Map) item;
      if(m.instanceOf(this)) return m;
    }
    throw castError(item, this, info);
  }

  @Override
  public boolean eq(final Type t) {
    if(this == t) return true;
    if(!(t instanceof MapType)) return false;
    final MapType mt = (MapType) t;
    return keyType().eq(mt.keyType()) && declType.eq(mt.declType);
  }

  @Override
  public boolean instanceOf(final Type t) {
    // the only non-function super-type of function is item()
    if(t == AtomType.ITEM || t == SeqType.ANY_MAP || t == SeqType.ANY_FUN) return true;
    if(!(t instanceof FuncType) || t instanceof ArrayType || this == SeqType.ANY_MAP) return false;

    final FuncType ft = (FuncType) t;
    final int al = argTypes.length;
    if(al != ft.argTypes.length || !declType.instanceOf(ft.declType)) return false;
    if(t instanceof MapType) return keyType().instanceOf(((MapType) t).keyType());

    // test function arguments of function type
    // example: map { 'x':'y' } instance of function(xs:string) as xs:string
    for(int a = 0; a < al; a++) {
      if(!argTypes[a].instanceOf(ft.argTypes[a])) return false;
    }
    return true;
  }

  @Override
  public Type union(final Type t) {
    if(instanceOf(t)) return t;
    if(t instanceof MapType) {
      final MapType mt = (MapType) t;
      if(mt.instanceOf(this)) return this;
      final AtomType a = (AtomType) keyType().intersect(mt.keyType());
      return a != null ? get(a, declType.union(mt.declType)) : SeqType.ANY_FUN;
    }
    return t instanceof ArrayType ? SeqType.ANY_FUN : t instanceof FuncType ? t.union(this) :
      AtomType.ITEM;
  }

  @Override
  public MapType intersect(final Type t) {
    // case for item() and compatible FuncType, e.g. function(xs:anyAtomicType) as item()*
    // also excludes FuncType.ANY_FUN
    if(instanceOf(t)) return this;
    if(t instanceof MapType) {
      final MapType mt = (MapType) t;
      if(mt.instanceOf(this)) return mt;
      final SeqType dt = declType.intersect(mt.declType);
      return dt == null ? null : get((AtomType) keyType().union(mt.keyType()), dt);
    }
    if(t instanceof FuncType) {
      final FuncType ft = (FuncType) t;
      if(ft.argTypes.length == 1 && ft.argTypes[0].instanceOf(SeqType.AAT)) {
        final SeqType dt = declType.intersect(ft.declType);
        return dt == null ? null : get((AtomType) keyType().union(ft.argTypes[0].type), dt);
      }
    }
    return null;
  }

  /**
   * Creates a new map type.
   * @param keyType key type
   * @param declType declared return type
   * @return map type
   */
  public static MapType get(final AtomType keyType, final SeqType declType) {
    return keyType == AtomType.AAT && declType.eq(SeqType.ITEM_ZM) ? SeqType.ANY_MAP :
      new MapType(keyType, declType);
  }

  /**
   * Returns the key type.
   * @return key type
   */
  public AtomType keyType() {
    return (AtomType) argTypes[0].type;
  }

  @Override
  public String toString() {
    final AtomType keyType = keyType();
    return keyType == AtomType.AAT && declType.eq(SeqType.ITEM_ZM) ? "map(*)"
        : "map(" + keyType + ", " + declType + ')';
  }
}
