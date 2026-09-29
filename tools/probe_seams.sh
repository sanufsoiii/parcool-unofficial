#!/usr/bin/env bash
# Decide, for the *target* Minecraft jar, which side of every ParCool version seam this port is
# on. Every line is a real assertion against `javap` on the mojmap jar - the output is a table of
# facts, not a judgement. Run after every Loom cache reset.
set -u
MC_RAW=$(cat /tmp/opencode/mc_raw.txt)
J="javap -p -s -cp $MC_RAW"

q() { # q <class> <regex>  -> prints matching member signatures, or NOTHING (the useful part)
  $J "$1" 2>/dev/null | grep -E "$2" || echo "    <ABSENT> $1 :: $2"
}
exists() { $J "$1" >/dev/null 2>&1 && echo "present" || echo "ABSENT"; }

echo "== ResourceLocation vs Identifier"
echo "  net.minecraft.resources.ResourceLocation  $(exists net.minecraft.resources.ResourceLocation)"
echo "  net.minecraft.resources.Identifier        $(exists net.minecraft.resources.Identifier)"

echo "== KeyMapping"
q net.minecraft.client.KeyMapping 'MAP;'
q net.minecraft.client.KeyMapping 'Category'
q net.minecraft.client.KeyMapping 'KeyMapping\('

echo "== Entity"
q net.minecraft.world.entity.Entity 'hurt'
q net.minecraft.world.entity.Entity 'isInWaterOrBubble|isInWater|isEyeInFluid'
q net.minecraft.world.entity.Entity 'save\(|load\('
q net.minecraft.world.entity.Entity 'getEyeHeight'

echo "== LivingEntity / Player"
q net.minecraft.world.entity.LivingEntity 'jumpFromGround'
q net.minecraft.world.entity.player.Player 'jumpFromGround'
q net.minecraft.world.entity.player.Player 'causeExtraKnockback'
q net.minecraft.world.entity.player.Player 'canInteractWithEntity'
q net.minecraft.world.entity.player.Player 'canPlayerFitWithinBlocksAndEntitiesWhen'
q net.minecraft.world.entity.player.Player 'createAttributes'
q net.minecraft.world.entity.LivingEntity 'getVisibilityPercent'

echo "== BlockEntityType"
q net.minecraft.world.level.block.entity.BlockEntityType 'Builder|register|<init>|BlockEntitySupplier'

echo "== Item / BlockItem / Item.Properties"
q net.minecraft.world.item.Item 'getDescriptionId'
q net.minecraft.world.item.BlockItem 'getDescriptionId'
q net.minecraft.world.item.Item\$Properties 'setId|useBlockDescriptionPrefix|noCollis|Component'

echo "== Registry / InteractionResult / Input"
q net.minecraft.core.Registry 'get\(|getValue'
q net.minecraft.world.InteractionResult 'interface|SUCCESS|enum'
q net.minecraft.world.entity.player.Input 'record|keyPresses'
q net.minecraft.client.player.KeyboardInput 'tick\('
q net.minecraft.client.player.LocalInput 'keyPresses'

echo "== BlockEntity / LevelChunk / LevelResource"
q net.minecraft.world.level.block.entity.BlockEntity 'loadAdditional|saveAdditional|loadWithComponents|preRemoveSideEffects'
q net.minecraft.world.level.storage.LevelResource '<init>'

echo "== Client render"
q net.minecraft.client.renderer.RenderType 'create\('
q net.minecraft.client.renderer.RenderStateShard 'CULL|NO_CULL|RENDERTYPE_LEASH_SHADER|NO_TEXTURE|LIGHTMAP'
q net.minecraft.client.renderer.RenderPipelines 'PIPELINES_BY_LOCATION|MATRICES_FOG_SNIPPET'
q net.minecraft.client.renderer.entity.EntityRenderer 'render\(|extractRenderState|submit\('
q net.minecraft.client.model.PlayerModel 'class|setupAnim'
q net.minecraft.client.renderer.entity.state.PlayerRenderState 'class'
q net.minecraft.client.renderer.entity.player.PlayerRenderer 'class'
q net.minecraft.client.Camera 'getXRot|getYRot|getUpVector|getLeftVector|setup\('
q net.minecraft.client.renderer.item.ItemTintSources 'class'
q net.minecraft.client.color.item.ItemColor 'class'

echo "== Commands / recipes / potions"
q net.minecraft.commands.Commands 'LEVEL_GAMEMASTERS'
q net.minecraft.commands.CommandSourceStack 'hasPermission|permissions\('
q net.minecraft.world.item.crafting.CustomRecipe 'canCraftInDimensions|assemble|input\(|output\('
q net.minecraft.world.item.crafting.ShapedRecipe 'Serializer|canCraftInDimensions'
q net.minecraft.world.item.potion.Potion 'Potion\('
q net.minecraft.world.item.alchemy.PotionBrewing 'Builder|build\('
q net.minecraft.world.level.block.state.BlockBehaviour 'onRemove'

echo "== Item assets / pack metadata"
q net.minecraft.client.resources.model.ClientItemInfoLoader 'class|LISTER'
q net.minecraft.server.packs.metadata.pack.PackMetadataSection 'fieldOf|optionalFieldOf'
q net.minecraft.SharedConstants 'DATA_PACK_FORMAT|RESOURCE_PACK_FORMAT'
