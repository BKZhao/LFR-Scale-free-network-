package epidemicOpinion;

import repast.simphony.context.Context;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.graph.NetworkGenerator;
import repast.simphony.context.space.graph.WattsBetaSmallWorldGenerator;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;

import java.util.*;
import java.io.IOException;

import org.apache.commons.collections15.BidiMap;
import edu.uci.ics.jung.algorithms.util.Indexer;

public class LFRContextBuilder implements ContextBuilder<Object> {
    
    private Network network;
    private LFRNetworkGenerator<Object> generator;
    
    @Override
    public Context build(Context<Object> context) {
        
        Parameters params = RunEnvironment.getInstance().getParameters();
        
        // 获取参数

        double mu = (Double) params.getValue("mu");
        double degree = (Double) params.getValue("degree");
        boolean exportNetwork = (Boolean) params.getValue("exportNetwork");        
        double posiStubbornRate = (Double) params.getValue("posiStubbornRate");
        double negaStubbornRate = (Double) params.getValue("negaStubbornRate");

        int agentNums = 1000;
        // 创建Agents并添加到context
        for (int i = 0; i < agentNums; i++) {
            Agent agent = new Agent(i);
            context.add(agent);
        }
        

        // 创建网络构建器
        NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("LFR Network", context, false); 
        network = netBuilder.buildNetwork();
        
        
        // 创建LFR网络生成器
        generator = new LFRNetworkGenerator<>(
            2.5,         // tau1 - 度分布指数
            1.5,         // tau2 - 社区大小分布指数  
            mu,           // mu - 混合参数
            (int)degree/2,            // 最小度
            (int)degree*2,           // 最大度
            degree,    		  // 平均度
            100,           // 最小社区大小
            250,           // 最大社区大小
            false         // 是否对称
        );
        
        // 生成LFR网络结构（这会直接在网络上创建边）
        network = generator.createNetwork(network);
        
        // 按照LFR网络中的社区，为每个Agent分配社区ID。
        // 因为LFR网络中已经生成的社区信息没办法直接映射在context中，因此需要额外分配社区ID
        assignCommunityToAgents();
        
        // 创建顽固观点社区并初始化所有agents
        initializeAgentsWithStubbornCommunities(0.01, posiStubbornRate, negaStubbornRate);

        //验证网络
       
        // 是否导出网络
        if (exportNetwork) {
            exportNetworkFiles();
       }
        
        Monitor monitor = new Monitor(context, network);
        context.add(monitor);
        
        
        return context;
    }
    
    /**
     * 为agents分配社区信息
     */
    private void assignCommunityToAgents() {
        // 创建节点映射
        Set<Object> nodeSet = new HashSet<>();
        for (Object node : network.getNodes()) {
            nodeSet.add(node);
        }
        BidiMap<Object, Integer> nodeMap = Indexer.create(nodeSet);
        
        // 获取社区分配信息
        Map<Object, Integer> nodeCommunityMap = generator.getNodeCommunityMap();
        
        // 为每个Agent设置社区ID
        for (Object node : network.getNodes()) {
            if (node instanceof Agent) {
                Agent agent = (Agent) node;
                int nodeIndex = nodeMap.get(node);
                
                // 从生成器的社区映射中获取社区ID
                Integer communityId = nodeCommunityMap.get(nodeIndex);
                if (communityId != null) {
                    agent.setCommunityId(communityId);
                } else {
                    // 默认社区ID，防止空指针
                    agent.setCommunityId(0);
                    System.out.println("Warning: No community assignment found for agent " + agent.getID());
                }
            }
        }    
    }
    
    /**
     * 打印网络统计信息（也是检查LFR网络是否映射成功）
     */
    private void printNetworkStatistics() {
        // 创建节点映射用于统计
        Set<Object> nodeSet = new HashSet<>();
        for (Object node : network.getNodes()) {
            nodeSet.add(node);
        }
        BidiMap<Object, Integer> nodeMap = Indexer.create(nodeSet);
        
        // 使用生成器的统计方法
        generator.printNetworkStatistics(network, nodeMap);
        
        // 额外的Agent特定统计
        System.out.println("\n==== Agent Statistics ====");
        Map<Integer, Integer> communityCount = new HashMap<>();
        for (Object node : network.getNodes()) {
            if (node instanceof Agent) {
                Agent agent = (Agent) node;
                int communityId = agent.getCommunityID();
                communityCount.put(communityId, communityCount.getOrDefault(communityId, 0) + 1);
            }
        }
        
        System.out.println("Communities assigned to agents: " + communityCount.size());
        System.out.println("Community distribution: " + communityCount);
        System.out.println("=============================\n");
    }

    /**
     * 初始化Agent的观点和感染状态
     * @param initialInfectionRate 初始感染率
     * @param posiStubbornRate 积极顽固者比例
     * @param negaStubbornRate 消极顽固者比例
     */
    public void initializeAgentsWithStubbornCommunities(double initialInfectionRate,
                                                        double posiStubbornRate,
                                                        double negaStubbornRate) {
        
  /*      System.out.println("\n=== 创建顽固观点社区 ===");*/
        
        // 收集所有agents并按社区分组
        List<Agent> allAgents = new ArrayList<>();
        Map<Integer, List<Agent>> communitiesMap = new HashMap<>();
        
        for (Object obj : network.getNodes()) {
            if (obj instanceof Agent) {
                Agent agent = (Agent) obj;
                allAgents.add(agent);
                
                int communityId = agent.getCommunityID();
                communitiesMap.computeIfAbsent(communityId, k -> new ArrayList<>()).add(agent);
            }
        }
        
        int totalAgents = allAgents.size();
        int posiStubbornCount = (int) Math.round(totalAgents * posiStubbornRate);
        int negaStubbornCount = (int) Math.round(totalAgents * negaStubbornRate);
        int initialInfectedCount = (int) Math.round(totalAgents * initialInfectionRate);
        
 /*       System.out.println("总人数: " + totalAgents);
        System.out.println("积极顽固者数量: " + posiStubbornCount);
        System.out.println("消极顽固者数量: " + negaStubbornCount);
        System.out.println("社区数量: " + communitiesMap.size());*/
        
        // 找出最大的两个社区作为顽固观点社区
        List<Map.Entry<Integer, List<Agent>>> sortedCommunities = new ArrayList<>(communitiesMap.entrySet());
        sortedCommunities.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        
        if (sortedCommunities.size() < 2) {
            System.err.println("错误：社区数量不足，无法创建顽固观点社区！");
            // 回退到原始方法
            initializeAgentsOriginal(initialInfectionRate, posiStubbornRate, negaStubbornRate);
            return;
        }
        
        // 选择最大的两个社区
        int posiStubbornCommunityId = sortedCommunities.get(0).getKey();
        int negaStubbornCommunityId = sortedCommunities.get(1).getKey();
        
        List<Agent> posiCommunityAgents = sortedCommunities.get(0).getValue();
        List<Agent> negaCommunityAgents = sortedCommunities.get(1).getValue();
        
  /*      System.out.println("积极顽固观点社区ID: " + posiStubbornCommunityId + " (人数: " + posiCommunityAgents.size() + ")");
        System.out.println("消极顽固观点社区ID: " + negaStubbornCommunityId + " (人数: " + negaCommunityAgents.size() + ")");*/
        
        //在积极社区中创建积极顽固者
        Collections.shuffle(posiCommunityAgents);
        int posiAssigned = 0;
        
        // 优先在积极社区中分配积极顽固者
        for (Agent agent : posiCommunityAgents) {
            if (posiAssigned < posiStubbornCount) {
                agent.initialize(1.0, true);  // 积极顽固者
                posiAssigned++;
            } else {
                // 剩余的agents设置随机观点
                double randomOpinion =  Math.random(); //积极观点社区大于0
                agent.initialize(randomOpinion, false);
            }
        }
        
        // 在消极社区中创建消极顽固者
        Collections.shuffle(negaCommunityAgents);
        int negaAssigned = 0;
        
        // 优先在消极社区中分配消极顽固者
        for (Agent agent : negaCommunityAgents) {
            if (negaAssigned < negaStubbornCount) {
                agent.initialize(-1.0, true);  // 消极顽固者
                negaAssigned++;
            } else {
                // 剩余的agents随机观点
                double randomOpinion =  Math.random() - 1; //消极观点社区中agent的观点都<0
                agent.initialize(randomOpinion, false);
            }
        }
        
        // 如果还有剩余的顽固者没有分配，分配到其他社区
        if (posiAssigned < posiStubbornCount || negaAssigned < negaStubbornCount) {
            List<Agent> remainingAgents = new ArrayList<>();
            for (int i = 2; i < sortedCommunities.size(); i++) {
                remainingAgents.addAll(sortedCommunities.get(i).getValue());
            }
            Collections.shuffle(remainingAgents);
            
            int remainingIndex = 0;
            
            // 分配剩余的积极顽固者
            while (posiAssigned < posiStubbornCount && remainingIndex < remainingAgents.size()) {
                remainingAgents.get(remainingIndex).initialize(1.0, true);
                posiAssigned++;
                remainingIndex++;
            }
            
            // 分配剩余的消极顽固者
            while (negaAssigned < negaStubbornCount && remainingIndex < remainingAgents.size()) {
                remainingAgents.get(remainingIndex).initialize(-1.0, true);
                negaAssigned++;
                remainingIndex++;
            }
        }
        
        //为其他社区的agents分配随机观点
        for (int i = 2; i < sortedCommunities.size(); i++) {
            List<Agent> communityAgents = sortedCommunities.get(i).getValue();
            
            for (Agent agent : communityAgents) {
                if (!agent.isStuborn()) {  // 只为非顽固者分配观点
                    double randomOpinion = Math.random() * 2 - 1;  // [-1, 1]
                    agent.initialize(randomOpinion, false);
                }
            }
        }
        
        // 随机分配初始感染者（可以与顽固者重叠）
        Collections.shuffle(allAgents);
        for (int i = 0; i < initialInfectedCount && i < totalAgents; i++) {
            Agent agent = allAgents.get(i);
            agent.setState(State.INFECTED);
        }
        
  /*      System.out.println("实际分配 - 积极顽固者: " + posiAssigned + ", 消极顽固者: " + negaAssigned);
        System.out.println("顽固观点社区创建完成！");
        System.out.println("========================\n");*/
    }
    
    /**
     * 原始的初始化方法（作为备用）
     */
    private void initializeAgentsOriginal(double initialInfectionRate,
                                         double posiStubbornRate,
                                         double negaStubbornRate) {
        
        List<Agent> allAgents = new ArrayList<>();
        for (Object obj : network.getNodes()) {
            if (obj instanceof Agent) {
                allAgents.add((Agent) obj);
            }
        }
        
        int totalAgents = allAgents.size();
        int posiStubbornCount = (int) Math.round(totalAgents * posiStubbornRate);
        int negaStubbornCount = (int) Math.round(totalAgents * negaStubbornRate);
        int initialInfectedCount = (int) Math.round(totalAgents * initialInfectionRate);
        
        Collections.shuffle(allAgents);
        
        int currentIndex = 0;        
        for (int i = 0; i < posiStubbornCount; i++) {
            Agent agent = allAgents.get(currentIndex++);
            agent.initialize(1, true);
        }
        
        for (int i = 0; i < negaStubbornCount; i++) {
            Agent agent = allAgents.get(currentIndex++);
            agent.initialize(-1, true);
        }
        
        // 初始感染者
        for (int i = 0; i < initialInfectedCount && i < totalAgents; i++) {
            Agent agent = allAgents.get(i);
            agent.setState(State.INFECTED);
        }
        
        // 非顽固者分配观点
        for (Agent agent : allAgents) {
            if (!agent.isStuborn()) {
                double random = Math.random() * 2 - 1;
                agent.initialize(random, false);
            }
        }
    }
    /**
     * 🔥 新增：验证社区观点分布
     */
    private void validateCommunityOpinionDistribution() {
        System.out.println("\n=== 社区观点分布验证 ===");
        
        Map<Integer, List<Double>> communityOpinions = new HashMap<>();
        Map<Integer, Integer> communityStubbornCount = new HashMap<>();
        
        // 收集每个社区的观点数据
        for (Object obj : network.getNodes()) {
            if (obj instanceof Agent) {
                Agent agent = (Agent) obj;
                int communityId = agent.getCommunityID();
                
                communityOpinions.computeIfAbsent(communityId, k -> new ArrayList<>())
                                .add(agent.getOpinion());
                
                if (agent.isStuborn()) {
                    communityStubbornCount.put(communityId, 
                        communityStubbornCount.getOrDefault(communityId, 0) + 1);
                }
            }
        }
        
        // 打印每个社区的统计信息
        for (Map.Entry<Integer, List<Double>> entry : communityOpinions.entrySet()) {
            int communityId = entry.getKey();
            List<Double> opinions = entry.getValue();
            
            double avgOpinion = opinions.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double minOpinion = opinions.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxOpinion = opinions.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            int stubbornCount = communityStubbornCount.getOrDefault(communityId, 0);
            
            System.out.println("社区 " + communityId + ":");
            System.out.println("  人数: " + opinions.size());
            System.out.println("  顽固者数量: " + stubbornCount);
            System.out.println("  平均观点: " + String.format("%.3f", avgOpinion));
            System.out.println("  观点范围: [" + String.format("%.3f", minOpinion) + 
                              ", " + String.format("%.3f", maxOpinion) + "]");
            
            // 判断社区类型
            if (stubbornCount > 0) {
                if (avgOpinion > 0.5) {
                    System.out.println("  类型: 积极顽固观点社区 ✅");
                } else if (avgOpinion < -0.5) {
                    System.out.println("  类型: 消极顽固观点社区 ✅");
                } else {
                    System.out.println("  类型: 混合顽固观点社区");
                }
            } else {
                System.out.println("  类型: 普通随机观点社区");
            }
            System.out.println();
        }
        
        System.out.println("社区观点分布验证完成");
        System.out.println("========================\n");
    }
    
    /**
     * 导出网络文件
     */
    private void exportNetworkFiles() {
        try {
            // 创建节点映射
            Set<Object> nodeSet = new HashSet<>();
            for (Object node : network.getNodes()) {
                nodeSet.add(node);
            }
            BidiMap<Object, Integer> nodeMap = Indexer.create(nodeSet);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // 导出为GML格式
            String gmlFile = "lfr_network_" + timestamp + ".gml";
            generator.exportToGML(network, nodeMap, gmlFile);
            System.out.println("Network exported to: " + gmlFile);
            
            // 导出为CSV格式
            String nodesFile = "lfr_nodes_" + timestamp + ".csv";
            String edgesFile = "lfr_edges_" + timestamp + ".csv";
            generator.exportToCSV(network, nodeMap, nodesFile, edgesFile);
            System.out.println("Network exported to: " + nodesFile + " and " + edgesFile);
            
        } catch (IOException e) {
            System.err.println("Error exporting network: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 验证网络质量
     */
    private void validateNetwork() {
        int nodeCount = network.size();
        int edgeCount = network.numEdges();
        
        System.out.println("\n=== Network Validation ===");
        System.out.println("Nodes: " + nodeCount);
        System.out.println("Edges: " + edgeCount);
        
        if (edgeCount == 0) {
            System.err.println("Warning: Network has no edges!");
            return;
        }
        
        // 检查孤立节点
        int isolatedNodes = 0;
        int totalDegree = 0;
        for (Object node : network.getNodes()) {
            int degree = network.getDegree(node);
            if (degree == 0) {
                isolatedNodes++;
            }
            totalDegree += degree;
        }
        
        if (isolatedNodes > 0) {
            System.out.println("Warning: " + isolatedNodes + " isolated nodes found");
        } else {
            System.out.println("No isolated nodes found");
        }
        
        // 验证度数统计
        double actualAvgDegree = nodeCount > 0 ? (double) totalDegree / nodeCount : 0;
        System.out.println("Actual average degree: " + String.format("%.2f", actualAvgDegree));
        
        // 验证社区分配
        int agentsWithCommunity = 0;
        Set<Integer> uniqueCommunities = new HashSet<>();
        for (Object node : network.getNodes()) {
            if (node instanceof Agent) {
                Agent agent = (Agent) node;
                if (agent.getCommunityID() >= 0) {
                    agentsWithCommunity++;
                    uniqueCommunities.add(agent.getCommunityID());
                }
            }
        }
        
        System.out.println("Agents with community assignment: " + agentsWithCommunity + "/" + nodeCount);
        System.out.println("Number of communities: " + uniqueCommunities.size());
        
        // 验证网络连通性基本信息
        if (nodeCount > 0 && edgeCount > 0) {
            double connectivity = (double) edgeCount / (nodeCount * (nodeCount - 1) / 2.0);
            System.out.println("Network density: " + String.format("%.4f", connectivity));
        }
        
        System.out.println("Network validation completed");
        System.out.println("=========================\n");
    }
    
    /**
     * 获取生成的网络
     */
    public Network<Object> getNetwork() {
        return network;
    }
    
    /**
     * 获取网络生成器
     */
    public LFRNetworkGenerator<Object> getGenerator() {
        return generator;
    }
}