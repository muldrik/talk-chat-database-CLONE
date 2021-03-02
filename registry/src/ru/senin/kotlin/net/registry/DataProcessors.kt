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

class SqlProcessor : DataProcessor {

    object UserInfos : IntIdTable() {
        val name = varchar("name", 50)
        val protocol: Column<String> = varchar("protocol", 50)
        val host: Column<String> = varchar("host", 50)
        val port: Column<Int> = integer("port")
    }

    object UsersAttempts : IntIdTable() {
        val name = varchar("name", 50)
        val numberOfAttempts: Column<Int> = integer("number_of_attempts")
    }

    class UserInfo(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserInfo>(UserInfos)

        var name by UserInfos.name
        var protocol by UserInfos.protocol
        var host by UserInfos.host
        var port by UserInfos.port
    }

    class UserAttempts(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserAttempts>(UsersAttempts)

        var name by UsersAttempts.name
        var numberOfAttempts by UsersAttempts.numberOfAttempts
    }

    init {
        Database.connect("jdbc:h2:./build/usersDatabase", driver = "org.h2.Driver")

        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(UserInfos)
            SchemaUtils.create(UsersAttempts)
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
            }
            UserAttempts.new {
                this.name = name
                numberOfAttempts = 0
            }
        }
    }

    override fun deleteUser(name: String) {
        transaction {
            UserInfos.deleteWhere { UserInfos.name eq name }
            UsersAttempts.deleteWhere { UsersAttempts.name eq name }
        }
    }

    override fun isUserRegistered(name: String): Boolean {
        var result = false
        transaction {
            result = (UserInfos.select { UserInfos.name eq name }.count() > 0)
        }
        return result
    }

    override fun getUsersMap(): Map<String, UserAddress> {
        lateinit var result: Map<String, UserAddress>
        transaction {
            result = UserInfo.all().associateBy({ it.name }, { userToAddress(it) })
        }
        return result
    }

    override fun clear() {
        transaction {
            UserInfos.deleteAll()
            UsersAttempts.deleteAll()
        }
    }

    override fun updateAttempts() {
        transaction {
            UserAttempts.all().forEach { user ->
                user.numberOfAttempts.let {
                    if (!checkUser(userToAddress(UserInfo.find { UserInfos.name eq user.name }.first())))
                        user.numberOfAttempts = it + 1
                    else
                        user.numberOfAttempts = 0
                }
                if (user.numberOfAttempts > 3) {
                    deleteUser(user.name)
                }
            }
        }
    }
}