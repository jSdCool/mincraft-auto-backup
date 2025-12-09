package com.backup.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.jsonrpc.JsonRpcNotificationService;
import net.minecraft.server.jsonrpc.OutgoingRpcMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(JsonRpcNotificationService.class)
public interface NotificationManagementListenerAccessor {
    @Invoker("broadcastNotification")
    void callNotifyAll(Holder.Reference<? extends OutgoingRpcMethod<Void, ?>> method);


}
