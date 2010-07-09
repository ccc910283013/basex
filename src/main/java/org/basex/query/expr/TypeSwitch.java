package org.basex.query.expr;

import static org.basex.query.QueryText.*;
import static org.basex.query.QueryTokens.*;
import java.io.IOException;
import org.basex.data.Serializer;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.item.Item;
import org.basex.query.item.Seq;
import org.basex.query.item.SeqType;
import org.basex.query.iter.Iter;
import org.basex.query.iter.SeqIter;
import org.basex.query.util.Var;
import org.basex.util.Array;

/**
 * Typeswitch expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
public final class TypeSwitch extends Expr {
  /** Typeswitch expression. */
  private Expr ts;
  /** Expression list. */
  private Case[] cs;

  /**
   * Constructor.
   * @param t typeswitch expression
   * @param c case expressions
   */
  public TypeSwitch(final Expr t, final Case[] c) {
    ts = t;
    cs = c;
  }

  @Override
  public Expr comp(final QueryContext ctx) throws QueryException {
    ts = checkUp(ts, ctx).comp(ctx);
    for(final Case c : cs) c.comp(ctx);

    boolean em = true;
    for(final Case c : cs) em &= c.e();
    if(em) {
      ctx.compInfo(OPTTRUE);
      return Seq.EMPTY;
    }

    final Expr[] tmp = new Expr[cs.length];
    for(int i = 0; i < cs.length; i++) tmp[i] = cs[i].expr;
    checkUp(ctx, tmp);

    // pre-evaluate type switch
    if(ts.i()) {
      for(int c = 0; c < cs.length; c++) {
        if(cs[c].var.type != null) {
          if(cs[c].var.type.instance(ts.iter(ctx))) {
            ctx.compInfo(OPTPRE, this);
            return cs[c].comp(ctx, (Item) ts).expr;
          }
          cs = Array.delete(cs, c);
        }
      }
    }
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) throws QueryException {
    final Iter seq = SeqIter.get(ctx.iter(ts));
    for(final Case c : cs) {
      seq.reset();
      final Iter iter = c.iter(ctx, seq);
      if(iter != null) return iter;
    }
    // will never happen
    return null;
  }

  @Override
  public boolean uses(final Use u, final QueryContext ctx) {
    if(u == Use.VAR) return true;
    for(final Case c : cs) if(c.uses(u, ctx)) return true;
    return ts.uses(u, ctx);
  }

  @Override
  public Expr remove(final Var v) {
    for(int c = 0; c < cs.length; c++) cs[c] = cs[c].remove(v);
    ts = ts.remove(v);
    return this;
  }

  @Override
  public SeqType returned(final QueryContext ctx) {
    final SeqType t = cs[0].returned(ctx);
    for(int l = 1; l < cs.length; l++) {
      if(!t.eq(cs[l].returned(ctx))) return SeqType.ITEM_ZM;
    }
    return t;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(TYPESWITCH + "(" + ts + ") ");
    for(int l = 0; l != cs.length; l++) sb.append((l != 0 ? ", " : "") + cs[l]);
    return sb.toString();
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this);
    for(final Case c : cs) c.plan(ser);
    ts.plan(ser);
    ser.closeElement();
  }
}
