import matplotlib.pyplot as plt
import networkx as nx

def generate_map_guide():
    # Set dark-themed styling
    plt.style.use('dark_background')
    fig, ax = plt.subplots(figsize=(10, 8))
    fig.patch.set_facecolor('#0F172A')
    ax.set_facecolor('#1E293B')
    
    # 1. Define the topological node-edge graph of the building
    # Nodes represent physical landmarks (Stairs, Library, etc.)
    # Edges represent walkable corridors
    G = nx.Graph()
    
    nodes = {
        "Staircase 2F": (2, 8),
        "Elevator Lobby": (5, 8),
        "Room 204": (8, 8),
        "Library Entrance": (8, 3),
        "Computer Lab": (5, 3),
        "Main Exit": (2, 3)
    }
    
    for name, pos in nodes.items():
        G.add_node(name, pos=pos)
        
    edges = [
        ("Staircase 2F", "Elevator Lobby"),
        ("Elevator Lobby", "Room 204"),
        ("Room 204", "Library Entrance"),
        ("Library Entrance", "Computer Lab"),
        ("Computer Lab", "Main Exit"),
        ("Main Exit", "Staircase 2F"),
        ("Elevator Lobby", "Computer Lab") # Connecting corridor
    ]
    G.add_edges_from(edges)
    
    pos = nx.get_node_attributes(G, 'pos')
    
    # Draw corridors (Gray edges)
    nx.draw_networkx_edges(G, pos, width=6, edge_color='#475569', ax=ax)
    # Draw highlighted active navigation path (Staircase -> Elevator -> Computer Lab -> Library)
    nav_edges = [("Staircase 2F", "Elevator Lobby"), ("Elevator Lobby", "Computer Lab"), ("Computer Lab", "Library Entrance")]
    nx.draw_networkx_edges(G, pos, edgelist=nav_edges, width=8, edge_color='#38BDF8', alpha=0.8, ax=ax)
    
    # Draw landmark nodes
    nx.draw_networkx_nodes(G, pos, node_size=700, node_color='#0F172A', edgecolors='#64748B', linewidths=2, ax=ax)
    
    # Label nodes with modern offset labels
    for name, (x, y) in pos.items():
        plt.text(x, y + 0.35, name, fontsize=10, fontweight='bold', color='white', ha='center',
                 bbox=dict(facecolor='#0F172A', edgecolor='#334155', boxstyle='round,pad=0.3'))
        
    # 2. Draw live user position ("Blue Dot") calculated by the ML model
    # User is currently on the "Elevator Lobby" -> "Computer Lab" corridor at 60% progress
    start_pos = nodes["Elevator Lobby"]
    end_pos = nodes["Computer Lab"]
    progress = 0.60
    
    user_x = start_pos[0] + progress * (end_pos[0] - start_pos[0])
    user_y = start_pos[1] + progress * (end_pos[1] - start_pos[1])
    
    # Draw glowing halo
    plt.scatter(user_x, user_y, color='#0284C7', s=600, alpha=0.3, zorder=5)
    # Draw solid blue dot
    plt.scatter(user_x, user_y, color='#38BDF8', s=150, edgecolors='white', linewidths=2, zorder=6, label="Predicted Location (Blue Dot)")
    
    # Draw directional path arrow
    plt.arrow(user_x, user_y - 0.1, 0, -0.6, head_width=0.15, head_length=0.2, fc='#38BDF8', ec='#38BDF8', zorder=7)
    
    # 3. Add Live Navigation Instructions Overlay
    instruction_text = (
        "🗺️ Live Map Guide\n"
        "-------------------------------\n"
        "📍 Current: Corridor [Elevator -> Lab]\n"
        "➡️ Direction: Walk straight 4m, then turn left at Computer Lab\n"
        "🎯 Destination: Library Entrance (12m away)"
    )
    plt.text(2.0, 1.5, instruction_text, fontsize=11, fontweight='bold', color='white',
             bbox=dict(facecolor='#1E293B', edgecolor='#0284C7', boxstyle='round,pad=0.8', linewidth=2))
             
    plt.xlim(1, 9)
    plt.ylim(0.5, 9.5)
    plt.axis('off')
    plt.title("Live Landmark Navigation & Wayfinding Graph", fontsize=14, fontweight='bold', pad=15, color='#38BDF8')
    
    # Save the output visualization
    output_png = "map_guide_network.png"
    plt.tight_layout()
    plt.savefig(output_png, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    print(f"Successfully generated map guide: '{output_png}'")
    plt.close()

if __name__ == "__main__":
    generate_map_guide()
