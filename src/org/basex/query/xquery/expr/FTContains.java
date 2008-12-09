package org.basex.query.xquery.expr;

import static org.basex.query.xpath.XPText.*;

import org.basex.data.Data;
import org.basex.index.FTIndexAcsbl;
import org.basex.index.FTTokenizer;
import org.basex.query.xquery.XQContext;
import org.basex.query.xquery.XQException;
import org.basex.query.xquery.item.Bln;
import org.basex.query.xquery.item.DNode;
import org.basex.query.xquery.item.FTNodeItem;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.item.Type;
import org.basex.query.xquery.iter.FTNodeIter;
import org.basex.query.xquery.iter.Iter;
import org.basex.query.xquery.iter.NodeIter;
import org.basex.query.xquery.path.Path;
import org.basex.query.xquery.path.SimpleIterStep;
import org.basex.query.xquery.util.Scoring;

/**
 * FTContains expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class FTContains extends Arr {
  /** Fulltext parser. */
  public final FTTokenizer ft = new FTTokenizer();
  /** Flag for index use. */
  private boolean iu;
  
  /**
   * Constructor.
   * @param ex contains, select and optional ignore expression
   */
  public FTContains(final Expr... ex) {
    super(ex);
  }

  /**
   * Constructor.
   * @param indexuse flag for index use
   * @param ex contains, select and optional ignore expression
   */
  private FTContains(final boolean indexuse, final Expr... ex) {
    super(ex);
    iu = indexuse;
  }
  
  
  @Override
  public Iter iter(final XQContext ctx) throws XQException {    
    if (iu) return iterIndex(ctx); 
    return iterWithOutIndex(ctx);
  }
  
  /**
   * Processing without using the index.
   * @param ctx context
   * @return iterator with results
   */
  public Iter iterIndex(final XQContext ctx) {
    
    // [SG] iterator variables must be locally defined; otherwise, they will
    //   have value assigned which refer to another call of this method

    return new Iter() {
      /** Iterator for path results. */
      NodeIter v1;
      
      @Override
      public Item next() throws XQException {
        if(v1 != null) return null;
        
        v1 = (NodeIter) ctx.iter(expr[0]);
        final FTTokenizer tmp = ctx.ftitem;
        ctx.ftitem = ft;
        
        final FTNodeIter fti = (FTNodeIter) ctx.iter(expr[1]);
        FTNodeItem ftn = fti.next();
        
        DNode n;
        while((n = (DNode) v1.next()) != null) {
          while (ftn != null && ftn.ftn.size > 0 && n.pre > ftn.ftn.getPre()) {
            ftn = fti.next();
          }
          if(ftn != null) {
            final boolean not = ftn.ftn.not;
            if(ftn.ftn.getPre() == n.pre) {
              ftn = null;
              ctx.ftitem = tmp;
              return new Bln(!not, n.score());
            }
            if(not) {
              ctx.ftitem = tmp;
              return new Bln(true, n.score());
            }
          }
        }
        ctx.ftitem = tmp;
        return Bln.FALSE;
      }
    };
  }
  
  /**
   * Processing without using the index.
   * @param ctx context
   * @return iterator with results
   * @throws XQException Exception
   */
  public Iter iterWithOutIndex(final XQContext ctx) throws XQException {
    final Iter iter = ctx.iter(expr[0]);
    final FTTokenizer tmp = ctx.ftitem;

    double d = 0;
    Item i;
    ctx.ftitem = ft;
    while((i = iter.next()) != null) {
      ft.init(i.str());
      final Item it = ctx.iter(expr[1]).next();
      d = Scoring.and(d, it.dbl());
    }
    ctx.ftitem = tmp;
    return new Bln(d != 0, d).iter();
  }

  @Override
  public void indexAccessible(final XQContext ctx, 
      final FTIndexAcsbl ia) throws XQException {
    if (!(ctx.item instanceof DNode)) return;

    // check if index exists
    final Data data = ((DNode) ctx.item).data;
    if(!(expr[0] instanceof SimpleIterStep && data.meta.ftxindex && 
        !data.meta.ftst && expr[1] instanceof FTExpr)) {
      ia.set(false, -1, false);
      return;
    }
    
    // check if index can be applied
    final SimpleIterStep path = (SimpleIterStep) expr[0];
    final boolean text = path.test.type == Type.TXT && path.expr.length == 0;
    if(!text) { // || !path.checkAxes()) {
      ia.set(false, -1, false);
      return;
    }
    ia.data = data;
    
    expr[1].indexAccessible(ctx, ia);    
  }
  
  @Override
  public Expr indexEquivalent(final XQContext ctx, final FTIndexEq ieq) {

    if(!(expr[0] instanceof SimpleIterStep)) return this;
    final SimpleIterStep sis = (SimpleIterStep) expr[0];
    final Expr ae = expr[1].indexEquivalent(ctx, ieq);
      
    ctx.compInfo(OPTFTINDEX);

    if (!ieq.seq) {
      // standard index evaluation
      Expr ex = new FTContainsIndex(ft, expr[0], ae);
      return Path.invertSIStep(sis, ieq.curr, ex);
    }
    
    // sequential evaluation
    // with index access
    return new FTContains(true, expr[0], ae);
  }
  
  @Override
  public Type returned() {
    return Type.BLN;
  }

  @Override
  public String color() {
    return "33CC33";
  }

  @Override
  public String toString() {
    return toString(" ftcontains ");
  }
}
