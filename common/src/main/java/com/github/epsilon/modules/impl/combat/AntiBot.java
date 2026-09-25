package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.ModuleDispatchMode;
import net.minecraft.world.entity.Entity;

/**
 * Anti Bot：判断实体是否为服务器未登记的假玩家。
 *
 * <p>本模块是纯查询服务，不监听任何事件，因此没有节点也没有 Part；调用方直接使用
 * {@link #isBot(Entity)}。这里不需要 {@code ModulePart}，也不应为了形式上的统一补一个空 Part。
 */
public class AntiBot extends Module {

    public static final AntiBot INSTANCE = new AntiBot();

    private AntiBot() {
        super("Anti Bot", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
    }

    public boolean isBot(Entity entity) {
        return isEnabled() && !mc.getConnection().getOnlinePlayerIds().contains(entity.getUUID());
    }

}
