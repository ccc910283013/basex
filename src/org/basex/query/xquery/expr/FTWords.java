package org.basex.query.xquery.expr;

import static org.basex.util.Token.*;
import java.io.IOException;

import org.basex.data.Serializer;
import org.basex.index.FTIndexAcsbl;
import org.basex.query.FTOpt.FTMode;
import org.basex.query.xquery.XQContext;
import org.basex.query.xquery.XQException;
import org.basex.query.xquery.item.Dbl;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.item.Str;
import org.basex.query.xquery.iter.Iter;
import org.basex.query.xquery.util.Scoring;
import org.basex.util.TokenBuilder;

/**
 * FTWords expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class FTWords extends FTExpr {
  /** Expression list. */
  public Expr query;
  /** Minimum and maximum occurrences. */
  final Expr[] occ;
  /** Search mode. */
  final FTMode mode;
  /** Fulltext token. */
  byte[] tok;

  /**
   * Constructor.
   * @param e expression
   * @param m search mode
   * @param o occurrences
   */
  public FTWords(final Expr e, final FTMode m, final Expr[] o) {
    query = e;
    mode = m;
    occ = o;
  }

  @Override
  public FTExpr comp(final XQContext ctx) throws XQException {
    occ[0] = ctx.comp(occ[0]);
    occ[1] = ctx.comp(occ[1]);
    query = ctx.comp(query);
    return this;
  }

  @Override
  public Iter iter(final XQContext ctx) throws XQException {
    final int len = contains(ctx);
    return Dbl.iter(len == 0 ? 0 : Scoring.word(ctx.ftitem.size(), len));
  }

  /**
   * Evaluates the fulltext match.
   * @param ctx query context
   * @return result of matching
   * @throws XQException xquery exception
   */
  private int contains(final XQContext ctx) throws XQException {
    final Iter iter = ctx.iter(query);
    final long mn = checkItr(ctx.iter(occ[0]));
    final long mx = checkItr(ctx.iter(occ[1]));
    int len = 0;
    int o = 0;
    Item i;

    switch(mode) {
      case ALL:
        while((i = iter.next()) != null) {
          final byte[] txt = i.str();
          final int oc = ctx.ftopt.contains(ctx.ftitem, ctx.ftpos, txt);
          if(oc == 0) return 0;
          len += txt.length * oc;
          o += oc / ctx.ftopt.sb.count();
        }
        break;
      case ALLWORDS:
        while((i = iter.next()) != null) {
          for(final byte[] txt : split(i.str(), ' ')) {
            final int oc = ctx.ftopt.contains(ctx.ftitem, ctx.ftpos, txt);
            if(oc == 0) return 0;
            len += txt.length * oc;
            o += oc;
          }
        }
        break;
      case ANY:
        while((i = iter.next()) != null) {
          final byte[] txt = i.str();
          final int oc = ctx.ftopt.contains(ctx.ftitem, ctx.ftpos, txt);
          len += txt.length * oc;
          o += oc / ctx.ftopt.sb.count();
        }
        break;
      case ANYWORD:
        while((i = iter.next()) != null) {
          for(final byte[] txt : split(i.str(), ' ')) {
            final int oc = ctx.ftopt.contains(ctx.ftitem, ctx.ftpos, txt);
            len += txt.length * oc;
            o += oc;
          }
        }
        break;
      case PHRASE:
        final TokenBuilder txt = new TokenBuilder();
        while((i = iter.next()) != null) {
          if(txt.size != 0) txt.add(' ');
          txt.add(i.str());
        }
        final int oc = ctx.ftopt.contains(ctx.ftitem, ctx.ftpos, txt.finish());
        len += txt.size * oc;
        o += oc / ctx.ftopt.sb.count();
        break;
    }
    return o < mn || o > mx ? 0 : Math.max(1, len);
  }

  @Override
  public void indexAccessible(final XQContext ctx, final FTIndexAcsbl ia) 
    throws XQException {
    // if the following conditions yield true, the index is accessed:
    // - no FTTimes option is specified
    ia.io = ia.io && 
    checkItr(ctx.iter(occ[0])) == 1 && 
    checkItr(ctx.iter(occ[1])) == Long.MAX_VALUE;

    if (!ia.io) return;
    
    if (query instanceof Str) {
      tok = ((Str) query).str();
      ctx.ftopt.sb.text = tok;
      int i = Integer.MAX_VALUE;
      // index size is incorrect for phrases
      while(ctx.ftopt.sb.more()) { // fto.sb.more()) {
        final int n = ia.data.nrIDs(ctx.ftopt.sb); //(fto.sb);
        if(n == 0) ia.indexSize = 0;
        i = Math.min(i, n);
      }
      ia.indexSize = i;
      ia.iu = true;
    } else {
      ia.io = false;
    }
  }

  @Override
  public Expr indexEquivalent(final XQContext ctx, final FTIndexEq ieq) {
    return new FTIndex(tok, mode, occ);
  }

  
  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this);
    query.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    return query.toString();
  }
}
