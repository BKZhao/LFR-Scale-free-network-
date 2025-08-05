# LFRContextBuilder

## Overview
`LFRContextBuilder` is a `ContextBuilder` implementation for Repast Simphony that constructs agent-based simulation contexts using the LFR (Lancichinetti-Fortunato-Radicchi) benchmark network model. It handles agent creation, network generation, community assignment, and initial state configuration.

## Key Functions
- Creates `Agent` instances and adds them to the simulation context
- Generates LFR networks via `LFRNetworkGenerator` with configurable parameters
- Maps network communities to individual agents
- Initializes agent properties (opinions, stubbornness, infection states) with community-based logic
- Supports network validation, statistics printing, and export (GML/CSV formats)

## Core Methods
- `build(Context<Object> context)`: Main entry point orchestrating context setup
- `assignCommunityToAgents()`: Maps LFR-generated communities to agents
- `initializeAgentsWithStubbornCommunities()`: Sets agent opinions/states (falls back to `initializeAgentsOriginal()` if insufficient communities)
- `exportNetworkFiles()`: Exports network data with timestamped filenames
- `validateNetwork()`: Checks network integrity (nodes, edges, isolated nodes, community distribution)

# LFRNetworkGenerator

## Overview
`LFRNetworkGenerator<T>` is a class implementing the LFR benchmark model, designed to generate synthetic networks with realistic community structures. This generator creates networks with power-law degree distributions and community size distributions, controlled by a mixing parameter (`mu`) that regulates the fraction of inter-community edges. 
<img width="1900" height="804" alt="image" src="https://github.com/user-attachments/assets/a20c07dd-6f7f-45f9-9915-5606a04ac7ae" />


## Key Features
- Generates networks with power-law degree distribution (controlled by `tau1`)
- Creates communities with power-law size distribution (controlled by `tau2`)
- Configurable mixing parameter (`mu`) for inter-community edge ratio
- Supports custom ranges for node degrees and community sizes
- Compatible with Repast Simphony's `Network` structure
- Maintains distribution properties while adjusting to target average degree

## Constructor Parameters
| Parameter       | Type    | Description                                                                 |
|-----------------|---------|-----------------------------------------------------------------------------|
| `tau1`          | double  | Power-law exponent for degree distribution (must be > 1)                    |
| `tau2`          | double  | Power-law exponent for community size distribution (must be > 1)            |
| `mu`            | double  | Mixing parameter (0-1) controlling fraction of inter-community edges        |
| `minDegree`     | int     | Minimum node degree (≥1)                                                   |
| `maxDegree`     | int     | Maximum node degree (≥ minDegree)                                          |
| `avgDegree`     | double  | Target average degree (between minDegree and maxDegree)                     |
| `minCommunity`  | int     | Minimum community size                                                     |
| `maxCommunity`  | int     | Maximum community size (≥ minCommunity)                                    |
| `isSymmetrical` | boolean | Whether the network is symmetrical                                          |

## Core Methods
- `createNetwork(Network<T> network)`: Main entry point that generates the complete LFR network, including degree sequences, communities, node assignments, and edges.
- `generateDegreeSequence(int numNodes, boolean isDirected)`: Generates power-law distributed node degrees with gentle adjustment to match target average degree.
- `generateCommunities(int numNodes)`: Creates community size distribution following power-law characteristics.
- `assignNodesToCommunities(int numNodes)`: Distributes nodes to communities while maintaining power-law size properties.
- `createEdges(Network<T> network, BidiMap<T, Integer> nodeMap)`: Builds edges with intra-community (1-μ) and inter-community (μ) fractions based on node degrees.

## Export & Analysis
- `exportToGML(Network<T> network, BidiMap<T, Integer> nodeMap, String filename)`: Exports network to GML format with node communities and edge types.
- `exportToCSV(Network<T> network, BidiMap<T, Integer> nodeMap, String nodesFilename, String edgesFilename)`: Exports node/edge data to CSV for statistical analysis.
- `calculateModularity(Network<T> network, BidiMap<T, Integer> nodeMap)`: Computes network modularity to evaluate community structure quality.



# ScaleFreeNetwork

## Overview
`ScaleFreeNetwork` is a class designed to generate scale-free networks using the Barabási-Albert (BA) model, tailored for agent-based simulations in Repast Simphony. Scale-free networks exhibit power-law degree distributions, where most nodes have few connections while a small number of hubs have many—mirroring real-world systems.

## Key Features
- Implements the Barabási-Albert algorithm with preferential attachment
- Generates networks with power-law degree distributions
- Configurable via target average degree parameter
- Automatically adjusts for small networks (falls back to complete graphs)
- Calculates and reports network statistics (edge count, actual average degree)
- Supports opinion leader selection based on node degrees

## Core Methods
- `buildScaleFreeNetwork(Network<Object> network, List<Agent> agents, double averageDegree)`: 
  Constructs the scale-free network by:
  - Creating an initial fully connected subgraph (m0 nodes)
  - Iteratively adding nodes with preferential attachment (m edges per new node)
  - Adjusting parameters for small agent populations

- `calculateEdgeCount(Network<Object> network, List<Agent> agents)`: 
  Computes total edge count (correcting for double-counting in undirected networks)

- `selectByProbability(List<Agent> agents, List<Double> probabilities, Random random, List<Agent> excludeList)`: 
  Implements preferential attachment by selecting nodes based on degree-weighted probabilities

- `selectOpinionLeaders(Network<Object> network, List<Agent> agents, double opRatio)`: 
  Identifies top-degree nodes as opinion leaders based on specified ratio

## BA Model Parameters
- Derived from target `averageDegree`:
  - `m`: Number of edges per new node (≈ averageDegree / 2)
  - `m0`: Initial connected subgraph size (≥ m+1, minimum 3)

## Usage Notes
- Automatically creates complete graphs for small agent counts (<= m0)
- Ensures no duplicate edges during network construction
- Includes fallback random selection if preferential attachment fails
- Prints network statistics (target/actual average degree, node/edge counts)

