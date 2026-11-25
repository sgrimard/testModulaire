package dev.testia.mono7.core.persistence

import dev.testia.mono7.core.spi.SchemaContribution
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.math.BigDecimal
import javax.sql.DataSource

@Entity
@Table(name = "product")
class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO

    @Column(name = "category", nullable = false)
    var category: String = ""
}

@Component
class CoreSchema : SchemaContribution {
    override val module = "core"
    override val order = 0
    override val scripts = listOf("db/core/001_product.sql")
}

/**
 * Migrateur minimal : chaque module déclare ses scripts (sans scan de classpath),
 * l'historique est tenu par (module, script). Le noyau passe en premier (order 0),
 * les plug-ins ensuite, ce qui permet leurs clés étrangères vers les tables du noyau.
 * Il s'exécute à la création du bean, avant que l'application ne reçoive des requêtes.
 */
@Component
class SchemaMigrator(
    private val dataSource: DataSource,
    private val contributions: ObjectProvider<SchemaContribution>,
) : InitializingBean {
    private val log = LoggerFactory.getLogger(SchemaMigrator::class.java)

    override fun afterPropertiesSet() = migrate()

    fun migrate() {
        dataSource.connection.use { c ->
            c.autoCommit = false
            // Verrou de session : plusieurs applis démarrant sur une base neuve migrent l'une après l'autre.
            c.createStatement().use { it.execute("select pg_advisory_xact_lock(4004)") }
            c.createStatement().use {
                it.execute(
                    "create table if not exists schema_history(" +
                        "module text not null, script text not null, applied_at timestamptz not null default now(), " +
                        "primary key (module, script))",
                )
            }
            val applied = c.prepareStatement("select module, script from schema_history").use { ps ->
                ps.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }
            }
            contributions.orderedStream().toList().sortedWith(compareBy({ it.order }, { it.module })).forEach { contribution ->
                contribution.scripts.filterNot { (contribution.module to it) in applied }.forEach { script ->
                    val sql = SchemaMigrator::class.java.classLoader.getResourceAsStream(script)
                        ?.use { it.readAllBytes().decodeToString() }
                        ?: error("Missing migration script $script (module ${contribution.module})")
                    c.createStatement().use { it.execute(sql) }
                    c.prepareStatement("insert into schema_history(module, script) values (?, ?)").use {
                        it.setString(1, contribution.module)
                        it.setString(2, script)
                        it.executeUpdate()
                    }
                    log.info("Applied {} ({})", script, contribution.module)
                }
            }
            c.commit()
        }
    }
}
