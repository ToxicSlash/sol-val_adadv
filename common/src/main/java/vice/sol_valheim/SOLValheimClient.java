package vice.sol_valheim;

public class SOLValheimClient
{
    static FoodHUD hud;
    public static void init() {
        FoodSync.initClient();
        hud = new FoodHUD();
    }
}
