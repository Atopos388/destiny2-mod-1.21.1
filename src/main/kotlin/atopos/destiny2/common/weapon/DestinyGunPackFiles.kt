package atopos.destiny2.common.weapon

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Small, clean-room gun-pack scanner based on TaCZ's external pack layout.
 *
 * Packs live in .minecraft/destiny_gunpacks and may be directories or ZIPs.
 * Their contents use normal resource-pack roots:
 *   assets/<namespace>/destiny_gunpacks/[gun].json
 *   assets/<namespace>/geo|textures|animations/...
 *   data/<namespace>/destiny_weapons/[gun].json
 *
 * A root gunpack.meta.json marks a directory/ZIP as a gun pack. No files are
 * extracted and no scripts from a pack are executed.
 */
object DestinyGunPackFiles {
    data class FileEntry(val packName: String, val path: String, val bytes: ByteArray)

    private val logger = LoggerFactory.getLogger("DestinyGunPackFiles")
    private const val META = "gunpack.meta.json"

    fun root(): Path = FabricLoader.getInstance().gameDir.resolve("destiny_gunpacks")

    fun scan(): List<FileEntry> {
        val root = root()
        runCatching { Files.createDirectories(root) }
            .onFailure {
                logger.error("Unable to create gun-pack directory {}", root, it)
                return emptyList()
            }

        val entries = mutableListOf<FileEntry>()
        Files.list(root).use { children ->
            children.sorted().forEach { path ->
                when {
                    Files.isDirectory(path) -> scanDirectory(path, entries)
                    path.fileName.toString().endsWith(".zip", ignoreCase = true) -> scanZip(path, entries)
                }
            }
        }
        return entries
    }

    fun serverDefinitions(): List<FileEntry> =
        scan().filter { entry ->
            entry.path.startsWith("data/") &&
                "/destiny_weapons/" in entry.path &&
                entry.path.endsWith(".json")
        }

    fun clientDefinitions(): List<FileEntry> =
        scan().filter { entry ->
            entry.path.startsWith("assets/") &&
                "/destiny_gunpacks/" in entry.path &&
                entry.path.endsWith(".json")
        }

    fun clientResource(location: ResourceLocation, snapshot: List<FileEntry> = scan()): ByteArray? {
        val path = "assets/${location.namespace}/${location.path}"
        return snapshot.lastOrNull { it.path == path }?.bytes
    }

    fun idFromDefinitionPath(path: String, directory: String): ResourceLocation? {
        val parts = path.split('/')
        if (parts.size < 4) return null
        val root = parts[0]
        if (root != "assets" && root != "data") return null
        val namespace = parts[1]
        val marker = parts.indexOf(directory)
        if (marker < 0 || marker >= parts.lastIndex) return null
        val relative = parts.subList(marker + 1, parts.size).joinToString("/")
            .removeSuffix(".json")
        return runCatching {
            ResourceLocation.fromNamespaceAndPath(namespace, relative)
        }.getOrNull()
    }

    private fun scanDirectory(pack: Path, output: MutableList<FileEntry>) {
        if (!Files.isRegularFile(pack.resolve(META))) return
        val packName = pack.fileName.toString()
        runCatching {
            Files.walk(pack).use { files ->
                files.filter(Files::isRegularFile).forEach { file ->
                    val relative = pack.relativize(file).toString().replace('\\', '/')
                    output += FileEntry(packName, relative, Files.readAllBytes(file))
                }
            }
        }.onFailure {
            logger.warn("Failed to read gun-pack directory {}", pack, it)
        }
    }

    private fun scanZip(pack: Path, output: MutableList<FileEntry>) {
        runCatching {
            ZipFile(pack.toFile()).use { zip ->
                if (zip.getEntry(META) == null) return@use
                val packName = pack.fileName.toString()
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .forEach { entry ->
                        val normalized = entry.name.replace('\\', '/').removePrefix("/")
                        if (".." in normalized.split('/')) return@forEach
                        output += FileEntry(
                            packName,
                            normalized,
                            zip.getInputStream(entry).use { it.readAllBytes() }
                        )
                    }
            }
        }.onFailure {
            logger.warn("Failed to read gun-pack ZIP {}", pack, it)
        }
    }
}
