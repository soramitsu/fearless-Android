package jp.co.soramitsu.runtime.network.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError

class RpcException(rpcError: RpcError) : Exception("Rpc error. code: ${rpcError.code}, message: ${rpcError.message}")
