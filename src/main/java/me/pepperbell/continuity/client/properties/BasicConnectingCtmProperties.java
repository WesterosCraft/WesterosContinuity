package me.pepperbell.continuity.client.properties;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.util.SpriteCalculator;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class BasicConnectingCtmProperties extends BaseCtmProperties {
	protected ConnectionPredicate connectionPredicate;

	protected Identifier connectTag;
	protected List<String> connectStateNames;

	public BasicConnectingCtmProperties(Properties properties, Identifier resourceId, PackResources pack, int packPriority, ResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Override
	public void init() {
		super.init();
		parseConnectTag();
		parseConnectStates();
		parseConnect();
		detectConnect();
		validateConnect();
	}

	protected void parseConnectTag() {
		String tagStr = properties.getProperty("connect_to_tag");
		if (tagStr != null) {
			connectTag = Identifier.tryParse(tagStr.trim());
			if (connectTag == null) {
				ContinuityClient.LOGGER.warn("Invalid 'connect_to_tag' value: '" + tagStr + "' in file '" + resourceId + "'");
			}
		}
	}

	protected void parseConnectStates() {
		String statesStr = properties.getProperty("connect_to_states");
		if (statesStr != null && !statesStr.isEmpty()) {
			connectStateNames = List.of(statesStr.trim().split("\\s*,\\s*"));
		}
	}

	protected void parseConnect() {
		if (connectTag != null && connectStateNames != null) {
			ConnectionPredicate tagPredicate = new TagConnectionPredicate(connectTag);
			ConnectionPredicate statePredicate = new StateConnectionPredicate(connectStateNames);
			connectionPredicate = new CompositeConnectionPredicate(tagPredicate, statePredicate);
			return;
		}
		if (connectTag != null) {
			connectionPredicate = new TagConnectionPredicate(connectTag);
			return;
		}
		if (connectStateNames != null) {
			ConnectionPredicate blockPredicate = ConnectionType.BLOCK;
			ConnectionPredicate statePredicate = new StateConnectionPredicate(connectStateNames);
			connectionPredicate = new CompositeConnectionPredicate(blockPredicate, statePredicate);
			return;
		}

		String connectStr = properties.getProperty("connect");
		if (connectStr == null) {
			return;
		}

		try {
			connectionPredicate = ConnectionType.valueOf(connectStr.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ContinuityClient.LOGGER.warn("Unknown 'connect' value '" + connectStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
		}
	}

	protected void detectConnect() {
		if (connectionPredicate == null) {
			if (matchBlocksPredicate != null) {
				connectionPredicate = ConnectionType.BLOCK;
			} else if (matchTilesSet != null) {
				connectionPredicate = ConnectionType.TILE;
			}
		}
	}

	protected void validateConnect() {
		if (connectionPredicate == null) {
			ContinuityClient.LOGGER.error("No valid connection type provided in file '" + resourceId + "' in pack '" + packId + "'");
			valid = false;
		}
	}

	public ConnectionPredicate getConnectionPredicate() {
		return connectionPredicate;
	}

	public enum ConnectionType implements ConnectionPredicate {
		BLOCK {
			@Override
			public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
				return appearanceState.getBlock() == otherAppearanceState.getBlock();
			}
		},
		TILE {
			@Override
			public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
				if (appearanceState == otherAppearanceState) {
					return true;
				}
				return SpriteCalculator.getSprites(otherAppearanceState, face).contains(quadSprite);
			}
		},
		STATE {
			@Override
			public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
				return appearanceState == otherAppearanceState;
			}
		};
	}

	public static class TagConnectionPredicate implements ConnectionPredicate {
		private final TagKey<Block> tag;

		public TagConnectionPredicate(Identifier tagId) {
			this.tag = TagKey.create(Registries.BLOCK, tagId);
		}

		@Override
		public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
			return appearanceState.is(tag) && otherAppearanceState.is(tag);
		}
	}

	public static class StateConnectionPredicate implements ConnectionPredicate {
		private final List<String> stateNames;

		public StateConnectionPredicate(List<String> stateNames) {
			this.stateNames = stateNames;
		}

		@Override
		public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
			Block block = appearanceState.getBlock();

			for (String stateName : stateNames) {
				Property<?> property = block.getStateDefinition().getProperty(stateName);
				if (property == null) return false;
				Property<?> otherProperty = otherAppearanceState.getBlock().getStateDefinition().getProperty(stateName);
				if (otherProperty == null) return false;
				Comparable<?> valueA = appearanceState.getValue(property);
				Comparable<?> valueB = otherAppearanceState.getValue(otherProperty);
				if (!valueA.equals(valueB)) return false;
			}
			return true;
		}
	}

	public static class CompositeConnectionPredicate implements ConnectionPredicate {
		private final ConnectionPredicate a, b;

		public CompositeConnectionPredicate(ConnectionPredicate a, ConnectionPredicate b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public boolean shouldConnect(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, Direction face, TextureAtlasSprite quadSprite) {
			return a.shouldConnect(level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite)
					&& b.shouldConnect(level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite);
		}
	}
}
