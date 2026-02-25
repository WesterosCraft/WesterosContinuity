package me.pepperbell.continuity.client.properties;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.util.SpriteCalculator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public class BasicConnectingCtmProperties extends BaseCtmProperties {
	protected ConnectionPredicate connectionPredicate;

	protected Identifier connectTag;
	protected List<String> connectStateNames;

	public BasicConnectingCtmProperties(Properties properties, Identifier resourceId, ResourcePack pack, int packPriority, ResourceManager resourceManager, String method) {
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
			public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
				return appearanceState.getBlock() == otherAppearanceState.getBlock();
			}
		},
		TILE {
			@Override
			public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
				if (appearanceState == otherAppearanceState) {
					return true;
				}
				return SpriteCalculator.getSprites(otherAppearanceState, face).contains(quadSprite);
			}
		},
		STATE {
			@Override
			public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
				return appearanceState == otherAppearanceState;
			}
		};
	}

	public static class TagConnectionPredicate implements ConnectionPredicate {
    private final TagKey<net.minecraft.block.Block> tag;

    public TagConnectionPredicate(Identifier tagId) {
      this.tag = TagKey.of(RegistryKeys.BLOCK, tagId);
    }

    @Override
    public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
    	return appearanceState.isIn(tag) && otherAppearanceState.isIn(tag);
    }
	}

	public static class StateConnectionPredicate implements ConnectionPredicate {
    private final List<String> stateNames;

    public StateConnectionPredicate(List<String> stateNames) {
      this.stateNames = stateNames;
    }

    @Override
    public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
    	Block block = appearanceState.getBlock();

      for (String stateName : stateNames) {
      	Property<?> property = block.getStateManager().getProperty(stateName);
      	if (property == null) return false;
        Comparable<?> valueA = appearanceState.get(property);
        Comparable<?> valueB = otherAppearanceState.get(property);
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
    public boolean shouldConnect(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, BlockState otherAppearanceState, BlockState otherState, BlockPos otherPos, Direction face, Sprite quadSprite) {
			return a.shouldConnect(blockView, appearanceState, state, pos, otherAppearanceState, otherState, otherPos, face, quadSprite)
					&& b.shouldConnect(blockView, appearanceState, state, pos, otherAppearanceState, otherState, otherPos, face, quadSprite);
    }
	}
}
