package jp.co.soramitsu.coredb.recovery.decision

/** Explicit choices; ordinary add never restores a detached or deleted owner. */
enum class EndpointAttachmentAction { ADD_NEW, REATTACH_EXACT_OWNED }
