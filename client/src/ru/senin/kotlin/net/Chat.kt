package ru.senin.kotlin.net

import ru.senin.kotlin.net.server.ChatClient
import ru.senin.kotlin.net.server.ChatMessageListener

class Chat(
    private val name: String,
    private val registry: RegistryApi
) : ChatMessageListener {

    private var exit = false
    private var selectedUser: String? = null
    private val clients = mutableMapOf<String, ChatClient>()
    private var users =  mutableMapOf<String, UserAddress>()

    private fun prompt(): String {
        val prompt = "  to [${selectedUser ?: "<not selected>"}] <<< "
        print(prompt)
        var value: String? = readLine()
        while (value.isNullOrBlank()) {
            print(prompt)
            value = readLine()
        }
        return value.trimStart()
    }

    private fun updateUsersList() {
        val registeredUsers = registry.list().execute().getOrNull()
        if (registeredUsers == null) {
            println("Cannot get users from registry")
            return
        }
        val aliveUserNames = registeredUsers.keys
        if (selectedUser != null && selectedUser !in aliveUserNames) {
            println("Selected user was removed from registry")
            selectedUser = null
        }
        users.clear()
        users.putAll(registeredUsers)
        clients.entries.retainAll { it.key in aliveUserNames }
        users.forEach { (name, address) ->
            println("$name ==> $address")
        }
    }

    private fun selectUser(userName: String) {
        val userAddress = users[userName]
        if (userAddress == null) {
            println("Unknown user '$userName'")
            return
        }
        if (userAddress.protocol !in ClientFactory.supportedProtocols()) {
            println("Protocol '${userAddress.protocol}' not supported")
            return
        }
        selectedUser = userName
    }

    private fun exit() {
        exit = true
    }

    private fun message(text: String) {
        val currentUser = selectedUser
        if (currentUser == null) {
            println("User not selected. Use :user command")
            return
        }
        val address = users[currentUser]
        if (address == null) {
            println("Cannot send message, because user disappeared")
            return
        }
        val client = clients.getOrPut(currentUser) {
            ClientFactory.create(address.protocol, address.host, address.port)
        }
        try {
            client.sendMessage(Message(name, text))
        }
        catch(e: Exception) {
            println("Error! ${e.message}")
        }
    }

    fun commandLoop() {
        var input: String
        printWelcome()
        updateUsersList()
        while (!exit) {
            input = prompt()
            when (input.substringBefore(" ")) {
                ":update" -> updateUsersList()
                ":exit" -> exit()
                ":user" -> {
                    val userName = input.split("""\s+""".toRegex()).drop(1).joinToString(" ")
                    selectUser(userName)
                }
                "" -> {}
                else -> message(input)
            }
        }
    }

    private fun printWelcome() {
        println(
            """      
                     \ | /
                 ^  -  O  -  
                / \^ / | \   Hi, $name
               /  / \        Welcome to Chat! 
              /  /   \ ============================   
            """.trimIndent()
        )
    }

    override fun messageReceived(userName: String, text: String) {
        println("\nfrom [$userName] >>> $text")
    }
}