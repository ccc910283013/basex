package org.basex.query.expr.gflwor;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;

import java.util.*;
import java.util.List;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.expr.gflwor.GFLWOR.*;
import org.basex.query.util.*;
import org.basex.query.util.collation.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * FLWOR {@code order by}-expression.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Leo Woerteler
 */
public final class OrderBy extends Clause {
  /** References to the variables to be sorted. */
  private VarRef[] refs;
  /** Sort keys. */
  private final Key[] keys;

  /**
   * Constructor.
   * @param refs variables to sort
   * @param keys sort keys
   * @param info input info
   */
  public OrderBy(final VarRef[] refs, final Key[] keys, final InputInfo info) {
    super(info, SeqType.ITEM_ZM);
    this.refs = refs;
    this.keys = keys;
  }

  @Override
  Eval eval(final Eval sub) {
    return new Eval() {
      /** Sorted output tuples. */
      private Value[][] tpls;
      /** Permutation of the values. */
      private Integer[] perm;
      /** Current position. */
      int pos;
      @Override
      public boolean next(final QueryContext qc) throws QueryException {
        if(tpls == null) sort(qc);
        if(pos == tpls.length) return false;
        final int p = perm[pos++];
        final Value[] tuple = tpls[p];
        // free the space occupied by the tuple
        tpls[p] = null;
        final int rl = refs.length;
        for(int r = 0; r < rl; r++) qc.set(refs[r].var, tuple[r]);
        return true;
      }

      /**
       * Caches and sorts all incoming tuples.
       * @param qc query context
       * @throws QueryException evaluation exception
       */
      private void sort(final QueryContext qc) throws QueryException {
        // keys are stored at odd positions, values at even ones
        List<Value[]> tuples = new ArrayList<>();
        while(sub.next(qc)) {
          final int kl = keys.length;
          final Item[] key = new Item[kl];
          for(int k = 0; k < kl; k++) key[k] = keys[k].expr.atomItem(qc, keys[k].info);
          tuples.add(key);

          final int rl = refs.length;
          final Value[] vals = new Value[rl];
          for(int r = 0; r < rl; r++) vals[r] = refs[r].value(qc);
          tuples.add(vals);
        }

        final int len = tuples.size() >>> 1;
        final Item[][] ks = new Item[len][];
        perm = new Integer[len];
        tpls = new Value[len][];
        for(int i = 0; i < len; i++) {
          perm[i] = i;
          tpls[i] = tuples.get(i << 1 | 1);
          ks[i] = (Item[]) tuples.get(i << 1);
        }
        // be nice to the garbage collector
        tuples = null;
        try {
          Arrays.sort(perm, (x, y) -> {
            try {
              final Item[] a = ks[x], b = ks[y];
              final int kl = keys.length;
              for(int k = 0; k < kl; k++) {
                final Key key = keys[k];
                Item m = a[k], n = b[k];
                if(m == Dbl.NAN || m == Flt.NAN) m = null;
                if(n == Dbl.NAN || n == Flt.NAN) n = null;
                if(m != null && n != null && !m.comparable(n))
                  throw castError(n, m.type, key.info);

                final int c = m == null
                    ? n == null ? 0                 : key.least ? -1 : 1
                    : n == null ? key.least ? 1 : -1 : m.diff(n, key.coll, key.info);
                if(c != 0) return key.desc ? -c : c;
              }
              return 0;
            } catch(final QueryException ex) {
              throw new QueryRTException(ex);
            }
          });
        } catch(final QueryRTException ex) {
          throw ex.getCause();
        }
      }
    };
  }

  @Override
  public boolean has(final Flag... flags) {
    for(final Key key : keys) if(key.has(flags)) return true;
    return false;
  }

  @Override
  public OrderBy compile(final CompileContext cc) throws QueryException {
    for(final Key key : keys) key.compile(cc);
    return this;
  }

  @Override
  public OrderBy optimize(final CompileContext cc) {
    return this;
  }

  @Override
  public boolean removable(final Var var) {
    for(final Key key : keys) if(!key.removable(var)) return false;
    return true;
  }

  @Override
  public VarUsage count(final Var var) {
    return VarUsage.sum(var, keys);
  }

  @Override
  public Clause inline(final Var var, final Expr ex, final CompileContext cc)
      throws QueryException {
    for(int r = refs.length; --r >= 0;) {
      if(var.is(refs[r].var)) refs = Array.delete(refs, r);
    }
    return inlineAll(keys, var, ex, cc) ? optimize(cc) : null;
  }

  @Override
  public OrderBy copy(final CompileContext cc, final IntObjMap<Var> vm) {
    return copyType(new OrderBy(Arr.copyAll(cc, vm, refs), Arr.copyAll(cc, vm, keys), info));
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return visitAll(visitor, keys);
  }

  @Override
  boolean clean(final IntObjMap<Var> decl, final BitArray used) {
    // delete unused variables
    final int len = refs.length;
    for(int r = refs.length; --r >= 0;) {
      if(!used.get(refs[r].var.id)) refs = Array.delete(refs, r);
    }
    if(refs.length == used.cardinality()) return refs.length != len;

    // add new variables, possible when an expression is inlined below this clause
    OUTER: for(int id = used.nextSet(0); id >= 0; id = used.nextSet(id + 1)) {
      for(final VarRef ref : refs) if(ref.var.id == id) continue OUTER;
      refs = Array.add(refs, new VarRef(info, decl.get(id)));
    }
    return true;
  }

  @Override
  boolean skippable(final Clause cl) {
    return cl instanceof Where;
  }

  @Override
  public void checkUp() throws QueryException {
    checkNoneUp(keys);
  }

  @Override
  void calcSize(final long[] minMax) {
  }

  @Override
  public int exprSize() {
    int sz = 0;
    for(final Expr e : refs) sz += e.exprSize();
    for(final Expr e : keys) sz += e.exprSize();
    return sz;
  }

  @Override
  public boolean equals(final Object obj) {
    if(this == obj) return true;
    if(!(obj instanceof OrderBy)) return false;
    final OrderBy o = (OrderBy) obj;
    return Array.equals(refs, o.refs) && Array.equals(keys, o.keys);
  }

  @Override
  public void plan(final FElem plan) {
    final FElem e = planElem();
    for(final Key key : keys) key.plan(e);
    plan.add(e);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(ORDER).append(' ').append(BY);
    final int kl = keys.length;
    for(int k = 0; k < kl; k++) sb.append(k == 0 ? " " : SEP).append(keys[k]);
    return sb.toString();
  }

  /**
   * Sort key.
   *
   * @author BaseX Team 2005-17, BSD License
   * @author Leo Woerteler
   */
  public static final class Key extends Single {
    /** Descending order flag. */
    private final boolean desc;
    /** Position of empty sort keys. */
    private final boolean least;
    /** Collation (can be {@code null}). */
    private final Collation coll;

    /**
     * Constructor.
     * @param info input info
     * @param key sort key expression
     * @param desc descending order
     * @param least empty least
     * @param coll collation (can be {@code null})
     */
    public Key(final InputInfo info, final Expr key, final boolean desc, final boolean least,
        final Collation coll) {
      super(info, key, SeqType.ITEM_ZM);
      this.desc = desc;
      this.least = least;
      this.coll = coll;
    }

    @Override
    public Key copy(final CompileContext cc, final IntObjMap<Var> vm) {
      return new Key(info, expr.copy(cc, vm), desc, least, coll);
    }

    @Override
    public Expr optimize(final CompileContext cc) throws QueryException {
      return this;
    }

    @Override
    public int exprSize() {
      return expr.exprSize();
    }

    @Override
    public boolean equals(final Object obj) {
      if(this == obj) return true;
      if(!(obj instanceof Key)) return false;
      final Key k = (Key) obj;
      return desc == k.desc && least == k.least && Objects.equals(coll, k.coll) &&
          super.equals(obj);
    }

    @Override
    public void plan(final FElem plan) {
      final FElem e = planElem(DIR, Token.token(desc ? DESCENDING : ASCENDING),
          Token.token(EMPTYORD), Token.token(least ? LEAST : GREATEST));
      expr.plan(e);
      plan.add(e);
    }

    @Override
    public String toString() {
      final TokenBuilder tb = new TokenBuilder(expr.toString());
      if(desc) tb.add(' ').add(DESCENDING);
      tb.add(' ').add(EMPTYORD).add(' ').add(least ? LEAST : GREATEST);
      if(coll != null) tb.add(' ').add(COLLATION).add(" \"").add(coll.uri()).add('"');
      return tb.toString();
    }
  }
}
