package vice.sol_valheim;

public class SOLValheimClient
{
    static FoodHUD hud;
    public static void init() {
        FoodSync.initClient();
        DietSync.initClient();
        hud = new FoodHUD();
    }
}
