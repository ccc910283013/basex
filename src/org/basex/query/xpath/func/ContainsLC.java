package org.basex.query.xpath.func;

import static org.basex.Text.*;
import static org.basex.query.xpath.XPText.*;
import org.basex.data.Data;
import org.basex.query.QueryException;
import org.basex.query.xpath.XPContext;
import org.basex.query.xpath.expr.Expr;
import org.basex.query.xpath.values.Bool;
import org.basex.query.xpath.values.NodeSet;
import org.basex.query.xpath.values.Item;
import org.basex.util.Token;

/**
 * Constructor for implementation specific containslc() function;
 * compares all tokens in lower case.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class ContainsLC extends Func {
  /** Name of function. */
  public static final String NAME = NAMESPACE + ":containslc";
  
  /**
   * Function Constructor.
   * @param arg expression array
   */
  public ContainsLC(final Expr[] arg) {
    super(arg, NAME + "(item, item)");
  }

  @Override
  public Bool eval(final XPContext ctx) throws QueryException {
    final Item[] v = evalArgs(ctx);
    // don't evaluate empty node sets
    final int s1 = v[0].size();
    final int s2 = v[1].size();
    if(s1 == 0 || s2 == 0) return Bool.FALSE;
    
    final byte[] lit = v[1].str();
    if(v[0] instanceof NodeSet && s2 == 1) {
      final NodeSet nodes = (NodeSet) v[0];
      final Data data = nodes.data;
      for(int n = 0; n < nodes.size; n++) {
        if(Token.containslc(data.atom(nodes.nodes[n]), lit)) return Bool.TRUE;
      }
      return Bool.FALSE;
    }
    return Bool.get(Token.containslc(v[0].str(), lit));
  }

  @Override
  public boolean checkArguments() {
    return args.length == 2;
  }

  @Override
  public Expr compile(final XPContext ctx) throws QueryException {
    super.compile(ctx);
    if(args[0] instanceof NodeSet && ((NodeSet) args[0]).size == 0) {
      ctx.compInfo(OPTFUNC, desc);
      return Bool.FALSE;
    }
    return this;
  }
}
