package epidemicOpinion;
import java.util.*;
import java.io.*;
import org.apache.commons.collections15.BidiMap;
import java.util.stream.StreamSupport;
import repast.simphony.context.space.graph.AbstractGenerator;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import edu.uci.ics.jung.algorithms.util.Indexer;

/**
 * LFRNetworkGenerator creates a Lancichinetti-Fortunato-Radicchi (LFR) benchmark network
 * which is commonly used for testing community detection algorithms.
 * 
 * The LFR model generates networks with power-law degree distribution and community sizes,
 * with a mixing parameter mu that controls the fraction of inter-community edges.
 * 
 * @author Bingkun Zhao
 * @param <T> the type of nodes in the network
 * @data 2025/6/3
 */
public class LFRNetworkGenerator<T> extends AbstractGenerator<T> {
    
    private final double tau1;        // Power-law exponent for degree distribution
    private final double tau2;        // Power-law exponent for community size distribution  
    private final double mu;          // Mixing parameter (fraction of inter-community edges)
    private final int minDegree;      // Minimum node degree
    private final int maxDegree;      // Maximum node degree
    private final int minCommunity;   // Minimum community size
    private final int maxCommunity;   // Maximum community size
    private final double avgDegree;   // Target average degree
    private final boolean isSymmetrical;
    
    // Internal structures
    private int[] degreeSequence;
    private List<Integer> originalCommunitySizes;
    private List<List<Integer>> communities;
    private Map<Object, Integer> nodeCommunityMap;
    private final Random random;
    
    /**
     * Constructs the LFR network generator with average degree parameter.
     */
    public LFRNetworkGenerator(double tau1, double tau2, double mu, 
                              int minDegree, int maxDegree, double avgDegree,
                              int minCommunity, int maxCommunity,
                              boolean symmetrical) {
        
        // Parameter validation
        if (tau1 <= 1.0) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("tau1 must be greater than 1"));
        }
        if (tau2 <= 1.0) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("tau2 must be greater than 1"));
        }
        if (mu < 0.0 || mu > 1.0) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("mu must be between 0 and 1"));
        }
        if (minDegree < 1) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("minDegree must be at least 1"));
        }
        if (maxDegree < minDegree) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("maxDegree must be >= minDegree"));
        }
        if (avgDegree < minDegree || avgDegree > maxDegree) {
            msg.error("Error creating LFRNetworkGenerator", 
                     new IllegalArgumentException("avgDegree must be between minDegree and maxDegree"));
        }
        
        this.tau1 = tau1;
        this.tau2 = tau2;
        this.mu = mu;
        this.minDegree = minDegree;
        this.maxDegree = maxDegree;
        this.avgDegree = avgDegree;
        this.minCommunity = minCommunity;
        this.maxCommunity = maxCommunity;
        this.isSymmetrical = symmetrical;
        this.random = new Random(RandomHelper.getSeed());
    }
    
    /**
     * Constructor with default values for community sizes.
     */
    public LFRNetworkGenerator(double tau1, double tau2, double mu, 
                              int minDegree, int maxDegree, double avgDegree,
                              boolean symmetrical) {
        this(tau1, tau2, mu, minDegree, maxDegree, avgDegree, 10, 50, symmetrical);
    }
    
    /**
     * Original constructor for backward compatibility.
     */
    public LFRNetworkGenerator(double tau1, double tau2, double mu, 
                              int minDegree, int maxDegree,
                              int minCommunity, int maxCommunity,
                              boolean symmetrical) {
        this(tau1, tau2, mu, minDegree, maxDegree, (minDegree + maxDegree) / 2.0,
             minCommunity, maxCommunity, symmetrical);
    }
    
    /**
     * Alternative constructor with default values for community sizes.
     */
    public LFRNetworkGenerator(double tau1, double tau2, double mu, 
                              int minDegree, int maxDegree, boolean symmetrical) {
        this(tau1, tau2, mu, minDegree, maxDegree, (minDegree + maxDegree) / 2.0, 10, 50, symmetrical);
    }
    
    @Override
    public Network<T> createNetwork(Network<T> network) {
        int numNodes = network.size();
        
        if (numNodes < 10) {
            msg.error("Error creating LFR network", 
                     new IllegalArgumentException("Number of nodes must be at least 10"));
        }
        
        // 验证平均度是否合理
        if (avgDegree >= numNodes) {
            msg.error("Error creating LFR network", 
                     new IllegalArgumentException("Average degree must be less than number of nodes"));
        }
        
        // Create node indexing
        Set<T> nodeSet = new HashSet<>();
        for (T node : network.getNodes()) {
            nodeSet.add(node);
        }
        BidiMap<T, Integer> nodeMap = Indexer.create(nodeSet);
        
        // Step 1: Generate degree sequence
        generateDegreeSequence(numNodes, network.isDirected());
        
        // Step 2: Generate community structure
        generateCommunities(numNodes);
        
        // Step 3: Assign nodes to communities
        assignNodesToCommunities(numNodes);
        
        // Step 4: Create edges
        createEdges(network, nodeMap);
        
 //       validateCommunityDistribution();
        
        return network;
    }
    
    /**
     * 修正后的度序列生成方法 - 保持幂律分布特性
     */
    private void generateDegreeSequence(int numNodes, boolean isDirected) {
        degreeSequence = new int[numNodes];
        
        // 使用更准确的幂律分布生成方法
        for (int i = 0; i < numNodes; i++) {
            degreeSequence[i] = generatePowerLawDegree();
        }
        
        // 轻微调整以接近目标平均度，但保持分布特性
        adjustDegreeSequenceGently(numNodes, isDirected);
    }
    
    /**
     * 生成单个幂律分布的度值
     */
    private int generatePowerLawDegree() {
        // 使用逆变换采样生成幂律分布
        double u = random.nextDouble();
        
        // 避免tau1接近1时的数值问题
        double exponent = 1.0 - tau1;
        if (Math.abs(exponent) < 1e-10) {
            // 当tau1接近1时，使用均匀分布
            return minDegree + random.nextInt(maxDegree - minDegree + 1);
        }
        
        // 计算归一化常数
        double minPow = Math.pow(minDegree, exponent);
        double maxPow = Math.pow(maxDegree, exponent);
        
        // 逆变换
        double x = minPow + u * (maxPow - minPow);
        int degree = (int) Math.round(Math.pow(x, 1.0 / exponent));
        
        // 确保在范围内
        return Math.max(minDegree, Math.min(maxDegree, degree));
    }
    
    /**
     * 温和调整度序列，保持分布特性的同时接近目标平均度
     */
    private void adjustDegreeSequenceGently(int numNodes, boolean isDirected) {
        double currentAvg = Arrays.stream(degreeSequence).average().orElse(0.0);
        double targetAvg = this.avgDegree;
        
 //       System.out.println("[DEBUG] Initial degree average: " + String.format("%.2f", currentAvg) + 
  //                        ", target: " + String.format("%.2f", targetAvg));
        
        // 如果当前平均度与目标相差不大，就不调整
        if (Math.abs(currentAvg - targetAvg) / targetAvg < 0.15) {
  //          System.out.println("[DEBUG] Degree sequence close to target, no adjustment needed");
        } else {
            // 计算调整因子，但限制其范围以保持分布特性
            double adjustFactor = Math.min(1.3, Math.max(0.8, targetAvg / currentAvg));
            
            // 应用调整
            for (int i = 0; i < numNodes; i++) {
                int newDegree = (int) Math.round(degreeSequence[i] * adjustFactor);
                degreeSequence[i] = Math.max(minDegree, Math.min(maxDegree, newDegree));
            }
            
            double adjustedAvg = Arrays.stream(degreeSequence).average().orElse(0.0);
  //          System.out.println("[DEBUG] Adjusted degree average: " + String.format("%.2f", adjustedAvg));
        }
        
        // 确保无向图的总度数为偶数
        if (!isDirected) {
            int totalDegree = Arrays.stream(degreeSequence).sum();
            if (totalDegree % 2 != 0) {
                // 随机选择一个节点增加1度
                int randomNode = random.nextInt(numNodes);
                if (degreeSequence[randomNode] < maxDegree) {
                    degreeSequence[randomNode]++;
                } else {
                    // 如果选中的节点已达最大度，寻找其他节点
                    for (int i = 0; i < numNodes; i++) {
                        if (degreeSequence[i] < maxDegree) {
                            degreeSequence[i]++;
                            break;
                        }
                    }
                }
            }
        }
        
        double finalAvg = Arrays.stream(degreeSequence).average().orElse(0.0);
   //     System.out.println("[DEBUG] Final degree average: " + String.format("%.2f", finalAvg));
    }
    
    /**
     * 修复后的社区生成方法 - 保存原始幂律分布大小
     */
    private void generateCommunities(int numNodes) {
        List<Integer> communitySizes = new ArrayList<>();
        int totalAssigned = 0;
        
        // 使用幂律分布生成社区大小
        while (totalAssigned < numNodes) {
            int size = generatePowerLawCommunitySize();
            
            if (totalAssigned + size <= numNodes) {
                communitySizes.add(size);
                totalAssigned += size;
            } else {
                // 处理剩余节点
                int remaining = numNodes - totalAssigned;
                if (remaining >= minCommunity) {
                    communitySizes.add(remaining);
                } else if (!communitySizes.isEmpty()) {
                    // 将剩余节点加入最后一个社区
                    int lastIdx = communitySizes.size() - 1;
                    communitySizes.set(lastIdx, communitySizes.get(lastIdx) + remaining);
                } else {
                    // 边界情况
                    communitySizes.add(Math.max(remaining, minCommunity));
                }
                totalAssigned = numNodes;
            }
        }
        
        // 初始化社区结构
        communities = new ArrayList<>();
        for (int size : communitySizes) {
            communities.add(new ArrayList<>()); // 创建空列表，稍后填充
        }
        
        // 重要：存储原始的幂律分布社区大小，供后续使用
        this.originalCommunitySizes = new ArrayList<>(communitySizes);
        
  //      System.out.println("[DEBUG] Generated " + communities.size() + 
 //                         " communities with sizes: " + communitySizes);
  //      System.out.println("[DEBUG] Size distribution: min=" + Collections.min(communitySizes) + 
  //                        ", max=" + Collections.max(communitySizes) + 
 //                         ", avg=" + communitySizes.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    
    /**
     * 生成幂律分布的社区大小
     */
    private int generatePowerLawCommunitySize() {
        double u = random.nextDouble();
        
        // 避免tau2接近1时的数值问题
        double exponent = 1.0 - tau2;
        if (Math.abs(exponent) < 1e-10) {
            // 当tau2接近1时，使用均匀分布
            return minCommunity + random.nextInt(maxCommunity - minCommunity + 1);
        }
        
        // 计算归一化常数
        double minPow = Math.pow(minCommunity, exponent);
        double maxPow = Math.pow(maxCommunity, exponent);
        
        // 逆变换
        double x = minPow + u * (maxPow - minPow);
        int size = (int) Math.round(Math.pow(x, 1.0 / exponent));
        
        return Math.max(minCommunity, Math.min(maxCommunity, size));
    }
    
    /**
     * 修复后的社区分配方法 - 使用存储的原始幂律大小
     */
    private void assignNodesToCommunities(int numNodes) {
        nodeCommunityMap = new HashMap<>();
        
        // 创建节点列表并随机打乱
        List<Integer> nodeList = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            nodeList.add(i);
        }
        Collections.shuffle(nodeList, random);
        
        // 按社区大小分配节点
        int nodeIndex = 0;
        
        // 使用存储的原始幂律分布社区大小
 //       System.out.println("[DEBUG] Using power-law community sizes: " + originalCommunitySizes);
        
        // 按照原始幂律大小分配节点
        for (int communityId = 0; communityId < communities.size(); communityId++) {
            List<Integer> community = communities.get(communityId);
            community.clear(); // 确保社区为空
            
            // 使用原始幂律分布的大小
            int targetSize = originalCommunitySizes.get(communityId);
            
            // 确保不会超出节点总数
            targetSize = Math.min(targetSize, numNodes - nodeIndex);
            
            // 分配节点到社区
            for (int i = 0; i < targetSize && nodeIndex < numNodes; i++) {
                int nodeId = nodeList.get(nodeIndex);
                community.add(nodeId);
                nodeCommunityMap.put(nodeId, communityId);
                nodeIndex++;
            }
        }
        
        // 处理剩余节点（如果有的话）
        while (nodeIndex < numNodes) {
            int nodeId = nodeList.get(nodeIndex);
            // 找到最小的社区添加节点
            int minSizeCommunity = getSmallestCommunityId();
            communities.get(minSizeCommunity).add(nodeId);
            nodeCommunityMap.put(nodeId, minSizeCommunity);
            nodeIndex++;
        }
        
        // 打印最终社区大小分布
        List<Integer> finalSizes = communities.stream().map(List::size).toList();
//        System.out.println("[DEBUG] Final community sizes: " + finalSizes);
 //       System.out.println("[DEBUG] Final size distribution: min=" + Collections.min(finalSizes) + 
 //                         ", max=" + Collections.max(finalSizes) + 
 //                         ", avg=" + finalSizes.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /**
     * 找出当前最小的社区ID
     */
    private int getSmallestCommunityId() {
        int minSize = Integer.MAX_VALUE;
        int minId = 0;
        
        for (int i = 0; i < communities.size(); i++) {
            int size = communities.get(i).size();
            if (size < minSize) {
                minSize = size;
                minId = i;
            }
        }
        
        return minId;
    }
    
    /**
     * 修正后的边创建方法 - 基于实际度序列计算目标边数
     */
    private void createEdges(Network<T> network, BidiMap<T, Integer> nodeMap) {
        boolean isDirected = network.isDirected();
        int numNodes = nodeMap.size();
        
        // 基于实际度序列计算目标边数
        int totalDegree = Arrays.stream(degreeSequence).sum();
        int targetEdges = isDirected ? totalDegree : totalDegree / 2;
        
 //       System.out.println("[DEBUG] Target edges: " + targetEdges + 
 //                         " (total degree: " + totalDegree + ")");
        
        // 跟踪每个节点的当前度数
        int[] currentDegrees = new int[numNodes];
        Set<String> createdEdges = new HashSet<>();
        
        // 创建边的优先级队列，按照剩余度数排序
        PriorityQueue<Integer> nodeQueue = new PriorityQueue<>((a, b) -> 
            Integer.compare(degreeSequence[b] - currentDegrees[b], 
                           degreeSequence[a] - currentDegrees[a]));
        
        for (int i = 0; i < numNodes; i++) {
            nodeQueue.offer(i);
        }
        
        int edgesCreated = 0;
        int maxIterations = targetEdges * 3;
        int iterations = 0;
        
        while (edgesCreated < targetEdges && !nodeQueue.isEmpty() && iterations < maxIterations) {
            Integer u = nodeQueue.poll();
            if (u == null) break;
            
            int remainingDegree = degreeSequence[u] - currentDegrees[u];
            if (remainingDegree <= 0) {
                iterations++;
                continue;
            }
            
            // 计算社区内外边数
            int intraDegree = (int) Math.round(remainingDegree * (1.0 - mu));
            int interDegree = remainingDegree - intraDegree;
            
            int sourceCommunity = nodeCommunityMap.get(u);
            
            // 添加社区内边
            int intraAdded = addEdgesWithinCommunity(network, nodeMap, u, sourceCommunity, 
                                                   intraDegree, currentDegrees, createdEdges, isDirected);
            
            // 添加社区间边
            int interAdded = addEdgesBetweenCommunities(network, nodeMap, u, sourceCommunity, 
                                                      interDegree, currentDegrees, createdEdges, isDirected);
            
            edgesCreated += intraAdded + interAdded;
            
            // 如果节点还有剩余度数，重新加入队列
            if (currentDegrees[u] < degreeSequence[u]) {
                nodeQueue.offer(u);
            }
            
            iterations++;
        }
        
 //       System.out.println("[DEBUG] Created " + edgesCreated + " edges out of " + targetEdges + 
 //                         " target (" + String.format("%.1f%%", 100.0 * edgesCreated / targetEdges) + ")");
        
        // 输出度分布统计
//        printDegreeStatistics(network, nodeMap);
    }
    
    /**
     * 在社区内添加边
     */
    private int addEdgesWithinCommunity(Network<T> network, BidiMap<T, Integer> nodeMap,
                                       int sourceIdx, int sourceCommunity, int targetCount,
                                       int[] currentDegrees, Set<String> createdEdges, boolean isDirected) {
        
        List<Integer> sameCommunity = new ArrayList<>(communities.get(sourceCommunity));
        sameCommunity.remove(Integer.valueOf(sourceIdx)); // 移除自己
        Collections.shuffle(sameCommunity, random);
        
        return addRandomEdges(network, nodeMap, sourceIdx, sameCommunity, targetCount, 
                            currentDegrees, createdEdges, isDirected);
    }
    
    /**
     * 在社区间添加边
     */
    private int addEdgesBetweenCommunities(Network<T> network, BidiMap<T, Integer> nodeMap,
                                         int sourceIdx, int sourceCommunity, int targetCount,
                                         int[] currentDegrees, Set<String> createdEdges, boolean isDirected) {
        
        List<Integer> otherNodes = new ArrayList<>();
        for (int c = 0; c < communities.size(); c++) {
            if (c != sourceCommunity) {
                otherNodes.addAll(communities.get(c));
            }
        }
        Collections.shuffle(otherNodes, random);
        
        return addRandomEdges(network, nodeMap, sourceIdx, otherNodes, targetCount, 
                            currentDegrees, createdEdges, isDirected);
    }
    
    /**
     * 添加随机边的核心方法
     */
    private int addRandomEdges(Network<T> network, BidiMap<T, Integer> nodeMap,
                              int sourceIdx, List<Integer> candidates, int count,
                              int[] currentDegrees, Set<String> createdEdges, boolean isDirected) {
        
        if (candidates.isEmpty() || count <= 0) return 0;
        
        T sourceNode = nodeMap.getKey(sourceIdx);
        int added = 0;
        
        for (int targetIdx : candidates) {
            if (added >= count) break;
            
            // 检查目标节点是否已达到度数上限
            if (currentDegrees[targetIdx] >= degreeSequence[targetIdx]) continue;
            
            T targetNode = nodeMap.getKey(targetIdx);
            
            // 生成边的标识符
            String edgeId = isDirected ? 
                sourceIdx + "->" + targetIdx : 
                Math.min(sourceIdx, targetIdx) + "-" + Math.max(sourceIdx, targetIdx);
            
            // 检查边是否已存在
            if (!createdEdges.contains(edgeId) && !network.isPredecessor(sourceNode, targetNode)) {
                network.addEdge(sourceNode, targetNode);
                createdEdges.add(edgeId);
                
                // 更新度数统计
                currentDegrees[sourceIdx]++;
                if (!isDirected) {
                    currentDegrees[targetIdx]++;
                }
                
                // 对于有向图，如果需要对称边
                if (isDirected && isSymmetrical && !network.isPredecessor(targetNode, sourceNode)) {
                    network.addEdge(targetNode, sourceNode);
                    String reverseEdgeId = targetIdx + "->" + sourceIdx;
                    createdEdges.add(reverseEdgeId);
                    currentDegrees[targetIdx]++;
                }
                
                added++;
            }
        }
        
        return added;
    }
    
    /**
     * 输出度分布统计信息
     */
    private void printDegreeStatistics(Network<T> network, BidiMap<T, Integer> nodeMap) {
        int[] actualDegrees = new int[nodeMap.size()];
        for (T node : network.getNodes()) {
            int nodeId = nodeMap.get(node);
            actualDegrees[nodeId] = network.getDegree(node);
        }
        
        double expectedAvg = Arrays.stream(degreeSequence).average().orElse(0.0);
        double actualAvg = Arrays.stream(actualDegrees).average().orElse(0.0);
        
        System.out.println("[DEBUG] Degree Statistics:");
        System.out.println("  Expected average: " + String.format("%.2f", expectedAvg));
        System.out.println("  Actual average: " + String.format("%.2f", actualAvg));
        System.out.println("  Expected range: [" + Arrays.stream(degreeSequence).min().orElse(0) + 
                          ", " + Arrays.stream(degreeSequence).max().orElse(0) + "]");
        System.out.println("  Actual range: [" + Arrays.stream(actualDegrees).min().orElse(0) + 
                          ", " + Arrays.stream(actualDegrees).max().orElse(0) + "]");
    }
    
    /**
     * Exports network to GML format.
     */
    public void exportToGML(Network<T> network, BidiMap<T, Integer> nodeMap, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("graph [");
            
            // Write graph properties
            writer.println("  directed " + (network.isDirected() ? 1 : 0));
            writer.println("  comment \"LFR Benchmark Network\"");
            writer.println("  avgDegree " + avgDegree);
            writer.println("  mu " + mu);
            writer.println();
            
            // Write nodes
            for (T node : network.getNodes()) {
                int nodeId = nodeMap.get(node);
                int community = nodeCommunityMap.get(nodeId);
                int degree = network.getDegree(node);
                
                writer.println("  node [");
                writer.println("    id " + nodeId);
                writer.println("    label \"" + node.toString() + "\"");
                writer.println("    community " + community);
                writer.println("    degree " + degree);
                writer.println("  ]");
            }
            
            writer.println();
            
            // Write edges
            Set<String> processedEdges = new HashSet<>();
            for (T source : network.getNodes()) {
                for (T target : network.getSuccessors(source)) {
                    int sourceId = nodeMap.get(source);
                    int targetId = nodeMap.get(target);
                    
                    String edgeKey = network.isDirected() ? 
                        sourceId + "->" + targetId : 
                        Math.min(sourceId, targetId) + "-" + Math.max(sourceId, targetId);
                    
                    if (!processedEdges.contains(edgeKey)) {
                        writer.println("  edge [");
                        writer.println("    source " + sourceId);
                        writer.println("    target " + targetId);
                        
                        // Determine if edge is intra or inter-community
                        int sourceCommunity = nodeCommunityMap.get(sourceId);
                        int targetCommunity = nodeCommunityMap.get(targetId);
                        String edgeType = (sourceCommunity == targetCommunity) ? "intra" : "inter";
                        writer.println("    type \"" + edgeType + "\"");
                        
                        writer.println("  ]");
                        processedEdges.add(edgeKey);
                    }
                }
            }
            
            writer.println("]");
        }
    }
    
    /**
     * Exports network to CSV format (edge list with node attributes).
     */
    public void exportToCSV(Network<T> network, BidiMap<T, Integer> nodeMap, 
                           String nodesFilename, String edgesFilename) throws IOException {
        
        // Export nodes
        try (PrintWriter nodeWriter = new PrintWriter(new FileWriter(nodesFilename))) {
            nodeWriter.println("id,label,community,degree,expected_degree");
            
            for (T node : network.getNodes()) {
                int nodeId = nodeMap.get(node);
                int community = nodeCommunityMap.get(nodeId);
                int actualDegree = network.getDegree(node);
                int expectedDegree = degreeSequence[nodeId];
                
                nodeWriter.println(nodeId + "," + 
                                 "\"" + node.toString() + "\"," + 
                                 community + "," + 
                                 actualDegree + "," + 
                                 expectedDegree);
            }
        }
        
        // Export edges
        try (PrintWriter edgeWriter = new PrintWriter(new FileWriter(edgesFilename))) {
            edgeWriter.println("source,target,type,source_community,target_community");
            
            Set<String> processedEdges = new HashSet<>();
            for (T source : network.getNodes()) {
                for (T target : network.getSuccessors(source)) {
                    int sourceId = nodeMap.get(source);
                    int targetId = nodeMap.get(target);
                    
                    String edgeKey = network.isDirected() ? 
                        sourceId + "->" + targetId : 
                        Math.min(sourceId, targetId) + "-" + Math.max(sourceId, targetId);
                    
                    if (!processedEdges.contains(edgeKey)) {
                        int sourceCommunity = nodeCommunityMap.get(sourceId);
                        int targetCommunity = nodeCommunityMap.get(targetId);
                        String edgeType = (sourceCommunity == targetCommunity) ? "intra" : "inter";
                        
                        edgeWriter.println(sourceId + "," + 
                                         targetId + "," + 
                                         edgeType + "," + 
                                         sourceCommunity + "," + 
                                         targetCommunity);
                        processedEdges.add(edgeKey);
                    }
                }
            }
        }
    }
    
    /**
     * Returns the community assignment for each node.
     */
    public Map<Object, Integer> getNodeCommunityMap() {
        return new HashMap<>(nodeCommunityMap);
    }
    
    /**
     * Returns the generated communities.
     */
    public List<List<Integer>> getCommunities() {
        return new ArrayList<>(communities);
    }
    
    /**
     * Returns the degree sequence.
     */
    public int[] getDegreeSequence() {
        return degreeSequence.clone();
    }
    
    /**
     * Returns the target average degree.
     */
    public double getAverageDegree() {
        return avgDegree;
    }
    
    /**
     * Returns the actual average degree of the generated network.
     */
    public double getActualAverageDegree(Network<T> network) {
        if (network.size() == 0) return 0.0;
        return StreamSupport.stream(network.getNodes().spliterator(), false)
                .mapToInt(network::getDegree)
                .average()
                .orElse(0.0);
    }
    
    /**
     * Prints network statistics.
     */
    public void printNetworkStatistics(Network<T> network, BidiMap<T, Integer> nodeMap) {
        System.out.println("=== LFR Network Statistics ===");
        System.out.println("Nodes: " + network.size());
        System.out.println("Edges: " + network.numEdges());
        System.out.println("Directed: " + network.isDirected());
        System.out.println("Target Average Degree: " + String.format("%.2f", avgDegree));
        System.out.println("Actual Average Degree: " + String.format("%.2f", getActualAverageDegree(network)));
        System.out.println("Mixing Parameter (mu): " + mu);
        System.out.println("Number of Communities: " + communities.size());
        
        // Community size statistics
        int[] communitySizes = communities.stream().mapToInt(List::size).toArray();
        System.out.println("Community Sizes: " + Arrays.toString(communitySizes));
        System.out.println("Average Community Size: " + 
                         String.format("%.2f", Arrays.stream(communitySizes).average().orElse(0.0)));
        
        // Modularity
        double modularity = calculateModularity(network, nodeMap);
        System.out.println("Modularity: " + String.format("%.4f", modularity));
        
        // Degree distribution statistics
        int[] degrees = StreamSupport.stream(network.getNodes().spliterator(), false)
                .mapToInt(network::getDegree)
                .toArray();
        Arrays.sort(degrees);
        System.out.println("Min Degree: " + degrees[0]);
        System.out.println("Max Degree: " + degrees[degrees.length - 1]);
        System.out.println("Median Degree: " + degrees[degrees.length / 2]);
        
        System.out.println("===============================");
    }
    
    /**
     * Calculates the modularity of the generated network.
     */
    public double calculateModularity(Network<T> network, BidiMap<T, Integer> nodeMap) {
        if (nodeCommunityMap == null) return 0.0;
        
        int totalEdges = network.numEdges();
        if (totalEdges == 0) return 0.0;
        
        double modularity = 0.0;
        
        for (T node1 : network.getNodes()) {
            int u = nodeMap.get(node1);
            int communityU = nodeCommunityMap.get(u);
            int degreeU = network.getDegree(node1);
            
            for (T node2 : network.getNodes()) {
                int v = nodeMap.get(node2);
                int communityV = nodeCommunityMap.get(v);
                int degreeV = network.getDegree(node2);
                
                double aij = network.isPredecessor(node1, node2) ? 1.0 : 0.0;
                double expected = (double) degreeU * degreeV / (2.0 * totalEdges);
                
                if (communityU == communityV) {
                    modularity += aij - expected;
                }
            }
        }
        
        return modularity / (2.0 * totalEdges);
    }
    /**
     * 验证社区大小是否遵循幂律分布
     */
    public void validateCommunityDistribution() {
        if (communities == null || communities.isEmpty()) return;
        
        // 计算社区大小
        int[] sizes = communities.stream().mapToInt(List::size).toArray();
        Arrays.sort(sizes);
        
        System.out.println("=== Community Size Distribution ===");
        System.out.println("Min size: " + sizes[0]);
        System.out.println("Max size: " + sizes[sizes.length - 1]);
        System.out.println("Mean size: " + Arrays.stream(sizes).average().orElse(0));
        
        // 计算幂律分布特征 - 对数空间中的斜率应接近-tau2
        Map<Integer, Integer> sizeCount = new HashMap<>();
        for (int size : sizes) {
            sizeCount.put(size, sizeCount.getOrDefault(size, 0) + 1);
        }
        
        System.out.println("Size distribution:");
        for (Map.Entry<Integer, Integer> entry : sizeCount.entrySet()) {
            System.out.println("Size " + entry.getKey() + ": " + entry.getValue() + " communities");
        }
        
        System.out.println("Expected power-law exponent: " + tau2);
        System.out.println("==============================");
    }
}