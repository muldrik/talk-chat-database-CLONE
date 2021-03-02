package ru.senin.kotlin.net.registry

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.senin.kotlin.net.Protocol
import ru.senin.kotlin.net.UserAddress
import java.util.concurrent.ConcurrentHashMap

class HashMapProcessor : DataProcessor {
    private val users = ConcurrentHashMap<String, UserAddress>()
    private val numberOfAttempts = ConcurrentHashMap<String, Int>()

    override fun addUser(name: String, userAddress: UserAddress) {
        users[name] = userAddress
        numberOfAttempts[name] = 0
    }

    override fun deleteUser(name: String) {
        users.remove(name)
        numberOfAttempts.remove(name)
    }

    override fun isUserRegistered(name: String): Boolean {
        return users.containsKey(name)
    }

    override fun getUsersMap(): Map<String, UserAddress> {
        return users
    }

    override fun clear() {
        users.clear()
    }

    override fun updateAttempts() {
        for ((user, address) in users) {
            numberOfAttempts[user]?.let {
                if (!checkUser(address))
                    numberOfAttempts[user] = it + 1
                else
                    numberOfAttempts[user] = 0
            }

            if (numberOfAttempts[user] == 3)
                deleteUser(user)
        }
    }
}

class SqlProcessor(path: String) : DataProcessor {

    object UserInfos : IntIdTable() {
        val name = varchar("name", 50).uniqueIndex()
        val protocol: Column<String> = varchar("protocol", 50)
        val host: Column<String> = varchar("host", 50)
        val port: Column<Int> = integer("port")
        val numberOfAttempts: Column<Int> = integer("number_of_attempts")
    }

    class UserInfo(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserInfo>(UserInfos)

        var name by UserInfos.name
        var protocol by UserInfos.protocol
        var host by UserInfos.host
        var port by UserInfos.port
        var numberOfAttempts by UserInfos.numberOfAttempts
    }

    init {
        Database.connect("jdbc:h2:$path", driver = "org.h2.Driver")

        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(UserInfos)
        }
    }

    private fun userToAddress(user: UserInfo): UserAddress {
        val protocol = when (user.protocol) {
            "http" -> Protocol.HTTP
            "ws" -> Protocol.WEBSOCKET
            "udp" -> Protocol.UDP
            else -> Protocol.HTTP
        }
        return UserAddress(protocol, user.host, user.port)
    }

    override fun addUser(name: String, userAddress: UserAddress) {
        transaction {
            UserInfo.new {
                this.name = name
                protocol = userAddress.protocol.protocol
                host = userAddress.host
                port = userAddress.port
                numberOfAttempts = 0
            }

        }
    }

    override fun deleteUser(name: String) {
        transaction {
            UserInfos.deleteWhere { UserInfos.name eq name }
        }
    }

    override fun isUserRegistered(name: String): Boolean {
        return transaction {
            (UserInfos.select { UserInfos.name eq name }.count() > 0)
        }
    }

    override fun getUsersMap(): Map<String, UserAddress> {
        return transaction {
            UserInfo.all().associateBy({ it.name }, { userToAddress(it) })
        }
    }

    override fun clear() {
        transaction {
            UserInfos.deleteAll()
        }
    }

    override fun updateAttempts() {
        transaction {
            UserInfo.all().forEach { user ->
                user.numberOfAttempts.let {
                    if (!checkUser(userToAddress(user)))
                        user.numberOfAttempts = it + 1
                    else
                        user.numberOfAttempts = 0
                }
                if (user.numberOfAttempts > 3) user.delete()
            }
        }
    }
}