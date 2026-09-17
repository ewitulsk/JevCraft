package dev.jevcraft.companion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/** Extends the vanilla sleep-percentage calculation with loaded, living Jev bodies in the same dimension. */
public final class CompanionSleepStatus {
    private CompanionSleepStatus() {}

    public static boolean hasCompanions(ServerLevel level) {
        for (var entity : level.getAllEntities()) if (entity instanceof JevCompanion jev && jev.isAlive() && !jev.isRemoved()) return true;
        return false;
    }

    public static boolean enough(ServerLevel level,int percentage,boolean deep) {
        int active=0,sleeping=0;
        for(var player:level.players())if(!player.isSpectator()){
            active++;if(deep?player.isSleepingLongEnough():player.isSleeping())sleeping++;
        }
        for(var entity:level.getAllEntities())if(entity instanceof JevCompanion jev&&jev.isAlive()&&!jev.isRemoved()){
            active++;if(deep?jev.companionSleepingLongEnough():jev.isSleeping())sleeping++;
        }
        return sleeping>=Math.max(1,Mth.ceil(active*percentage/100.0F));
    }

    public static void wakeCompanions(ServerLevel level) {
        for(var entity:level.getAllEntities())if(entity instanceof JevCompanion jev&&jev.isSleeping())jev.stopSleeping();
    }
}
