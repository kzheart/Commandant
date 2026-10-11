package fixture.common;
/** Deliberately no Kether capability: foreign OpenAPI returns public boolean getter. */
public final class OpenAPI {
 public static int calls;
 public static ForeignResult call(String channel,Object... data){calls++;System.out.println("NON_KETHER_FIXTURE_CHANNEL="+channel);return new ForeignResult();}
 public static final class ForeignResult {
  public boolean isSuccessful(){return false;}
  public Object getValue(){return null;}
 }
}
