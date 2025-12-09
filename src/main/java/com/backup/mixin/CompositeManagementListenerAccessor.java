package com.backup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.notifications.NotificationService;

@Mixin(NotificationManager.class)
public interface CompositeManagementListenerAccessor {
    @Accessor
    List<NotificationService> getNotificationServices();
}
