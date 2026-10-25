package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.chain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/** Actual catalog methods and bounded node reader; only Room query replies are controlled. */
class ChainDaoCatalogReadCostTest {
    private class Fixture(count:Int=106) {
        val chains=(0 until count).map { i -> ChainLocal("chain-$i",null,null,null,"Chain $i",null,"",null,42,false,false,false,
            false,false,false,false,false,null,"Substrate",null,null,null,null) }
        val assets=chains.flatMap { chain -> listOf("0","1","2").map { id ->
            ChainAssetLocal(id,"Asset $id","A$id",chain.id,"",null,"UNSUPPORTED",12,null,id=="0",null,null,null,null,true,null,null)
        } }.toMutableList()
        val explorers=chains.map { chain -> ChainExplorerLocal(chain.id,"subscan","[]","https://public.invalid/") }.toMutableList()
        val nodes=chains.flatMap { chain -> (0..2).map { i ->
            ChainNodeLocal(chain.id,"wss://node-$i.${chain.id}.invalid/","Node $i",i==1,i!=2)
        } }
        val calls=mutableListOf<String>()
        val targets=mutableListOf<Pair<String,String?>>()
        val catalogFlags=mutableListOf<Boolean>()
        var chunks=0
        val dao:ChainDao=mock(ChainDao::class.java,withSettings().useConstructor().defaultAnswer { call ->
            val name=call.method.name
            when(name) {
                "getEndpointMutationLane","getJoinChainInfo","getAssetsConfigs","readRuntimeNodes" -> call.callRealMethod()
                "runtimeChainPage" -> {
                    calls+=name
                    val chain=call.getArgument<String?>(0); val after=call.getArgument<Long?>(1)
                    chains.mapIndexed { i,v -> RuntimeChainCatalogRow(v,i.toLong()+1,null,null) }
                        .filter { (chain==null || it.chain.id==chain) && (after==null || it.sourceRowId>after) }.take(64)
                }
                "runtimeAssetPage" -> {
                    calls+=name
                    val chain=call.getArgument<String?>(0); val after=call.getArgument<Long?>(1)
                    targets+=name to chain
                    val catalogOnly=call.getArgument<Boolean>(2);catalogFlags+=catalogOnly
                    assets.mapIndexed { i,v -> RuntimeAssetCatalogRow(v,i.toLong()+1,null,null,null,null) }
                        .filter { (chain==null || it.asset.chainId==chain) && (after==null || it.sourceRowId>after) &&
                            (!catalogOnly || chains.any { parent -> parent.id==it.asset.chainId }) }.take(64)
                }
                "runtimeExplorerPage" -> {
                    calls+=name
                    val chain=call.getArgument<String?>(0); val after=call.getArgument<Long?>(1)
                    targets+=name to chain
                    explorers.mapIndexed { i,v -> RuntimeExplorerCatalogRow(v,i.toLong()+1,2,"[]".toByteArray()) }
                        .filter { (chain==null || it.explorer.chainId==chain) && (after==null || it.sourceRowId>after) &&
                            chains.any { parent -> parent.id==it.explorer.chainId } }.take(64)
                }
                "runtimeNodeMetadata" -> {
                    calls+=name
                    val chain=call.getArgument<String>(0); val after=call.getArgument<Long?>(1)
                    nodes.mapIndexed { i,v -> i.toLong()+1 to v }
                        .filter { it.second.chainId==chain && (after==null || it.first>after) }.take(64)
                        .map { (id,v) -> RuntimeNodeMetadata(id,v.chainId.toByteArray().size.toLong(),v.url.toByteArray().size.toLong(),
                            v.name.toByteArray().size.toLong(),if(v.isActive)1 else 0,if(v.isDefault)1 else 0,v.url.toByteArray(),v.name.toByteArray()) }
                }
                "runtimeNodeUrlChunk","runtimeNodeNameChunk" -> {
                    calls+=name;chunks++
                    val id=call.getArgument<Long>(1); val offset=call.getArgument<Int>(2);val length=call.getArgument<Int>(3)
                    val node=nodes[(id-1).toInt()]
                    val bytes=(if(name=="runtimeNodeUrlChunk")node.url else node.name).toByteArray()
                    bytes.copyOfRange(offset-1,offset-1+length)
                }
                else -> error("Unexpected call: $name")
            }
        })
    }

    @Test fun full106ChainCatalogUsesAtMost225BoundedQueries()=runBlocking<Unit> {
        val f=Fixture();val result=f.dao.getJoinChainInfo()
        assertEquals(106,result.size)
        assertEquals(f.chains,result.map{it.chain})
        assertEquals(f.assets,result.flatMap{it.assets})
        assertEquals(f.explorers,result.flatMap{it.explorers})
        assertEquals(f.nodes,result.flatMap{it.nodes})
        assertTrue("Queries: ${f.calls.size}",f.calls.size<=225)
        assertEquals(0,f.chunks)
    }
    @Test fun fullCatalogScansAssetAndExplorerTablesOnceAcrossPages()=runBlocking<Unit> {
        val f=Fixture();f.dao.getJoinChainInfo()
        assertTrue(f.targets.all{it.second==null})
        assertEquals(6,f.calls.count{it=="runtimeAssetPage"})
        assertEquals(3,f.calls.count{it=="runtimeExplorerPage"})
    }
    @Test fun oneChainLookupRetainsOnlyRequestedChildrenAndSelectedNode()=runBlocking<Unit> {
        val f=Fixture();val result=requireNotNull(f.dao.getJoinChainInfo("chain-70"))
        assertEquals("chain-70",result.chain.id)
        assertEquals(f.assets.filter{it.chainId=="chain-70"},result.assets)
        assertEquals(f.explorers.filter{it.chainId=="chain-70"},result.explorers)
        assertEquals(f.nodes.filter{it.chainId=="chain-70"},result.nodes)
        assertTrue(f.targets.all{it.second=="chain-70"})
        assertEquals(1,result.nodes.count{it.isActive})
    }
    @Test fun missingParentPerformsNoChildRead()=runBlocking<Unit> {
        val f=Fixture();assertNull(f.dao.getJoinChainInfo("missing"))
        assertEquals(listOf("runtimeChainPage"),f.calls)
    }
    @Test fun emptyCatalogPerformsNoChildRead()=runBlocking<Unit> {
        val f=Fixture(0);assertTrue(f.dao.getJoinChainInfo().isEmpty())
        assertEquals(listOf("runtimeChainPage"),f.calls)
    }
    @Test fun orphanChildrenCannotEnterUnrelatedParent()=runBlocking<Unit> {
        val f=Fixture(1)
        f.assets+=f.assets.first().copy(chainId="orphan")
        f.explorers+=f.explorers.first().copy(chainId="orphan")
        val result=f.dao.getJoinChainInfo().single()
        assertTrue(f.catalogFlags.isNotEmpty() && f.catalogFlags.all { it })
        assertEquals(3,result.assets.size);assertEquals(1,result.explorers.size)
        assertTrue(result.assets.all{it.chainId==result.chain.id})
        assertTrue(result.explorers.all{it.chainId==result.chain.id})
    }
    @Test fun genericAssetConfigReaderKeepsItsOriginalAllRowSemantics()=runBlocking<Unit> {
        val f=Fixture(1);f.assets+=f.assets.first().copy(chainId="orphan")
        assertEquals(f.assets,f.dao.getAssetsConfigs())
        assertTrue(f.catalogFlags.isNotEmpty() && f.catalogFlags.none { it })
    }
}
