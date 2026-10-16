
package com.noxmusic.app

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

private val Fondo = Color(0xFF09070F)
private val Panel = Color(0xFF191321)
private val Morado = Color(0xFF9B5CFF)
private val Texto = Color(0xFFF7F1FF)
private val Secundario = Color(0xFFB0A5BD)

private data class Cancion(
    val id: Long,
    val titulo: String,
    val artista: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {

    private lateinit var reproductor: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reproductor = ExoPlayer.Builder(this).build()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Morado,
                    background = Fondo,
                    surface = Panel
                )
            ) {
                NoxMusic(
                    cargarMusica = { cargarCanciones() },
                    reproductor = reproductor
                )
            }
        }
    }

    private fun cargarCanciones(): List<Cancion> {
        val resultado = mutableListOf<Cancion>()
        val coleccion = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val columnas = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        contentResolver.query(
            coleccion,
            columnas,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val tituloCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistaCol = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val titulo = cursor.getString(tituloCol)
                    ?: "Canción desconocida"
                val artista = cursor.getString(artistaCol)
                    ?.takeUnless { it == "<unknown>" }
                    ?: "Artista desconocido"

                resultado.add(
                    Cancion(
                        id = id,
                        titulo = titulo,
                        artista = artista,
                        uri = ContentUris.withAppendedId(coleccion, id)
                    )
                )
            }
        }

        return resultado
    }

    override fun onDestroy() {
        if (::reproductor.isInitialized) {
            reproductor.release()
        }
        super.onDestroy()
    }
}

@Composable
private fun NoxMusic(
    cargarMusica: () -> List<Cancion>,
    reproductor: ExoPlayer
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current

    val preferencias = remember {
        contexto.getSharedPreferences("nox_music", 0)
    }

    val permiso = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var permisoConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                contexto,
                permiso
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var canciones by remember { mutableStateOf(emptyList<Cancion>()) }
    var busqueda by remember { mutableStateOf("") }
    var pestaña by remember { mutableStateOf(0) }
    var actual by remember { mutableStateOf<Cancion?>(null) }
    var reproduciendo by remember { mutableStateOf(false) }
    var favoritos by remember {
        mutableStateOf(
            preferencias.getStringSet("favoritos", emptySet())
                ?.toSet() ?: emptySet()
        )
    }

    val solicitarPermiso =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->
            permisoConcedido = concedido
            if (concedido) canciones = cargarMusica()
        }

    DisposableEffect(reproductor) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reproduciendo = isPlaying
            }
        }
        reproductor.addListener(listener)
        onDispose {
            reproductor.removeListener(listener)
        }
    }

    LaunchedEffect(permisoConcedido) {
        if (permisoConcedido) canciones = cargarMusica()
    }

    fun reproducir(cancion: Cancion) {
        actual = cancion
        val indice = canciones.indexOf(cancion).coerceAtLeast(0)
        reproductor.setMediaItems(
            canciones.map { MediaItem.fromUri(it.uri) },
            indice,
            0L
        )
        reproductor.prepare()
        reproductor.play()
    }

    fun cambiarFavorito(cancion: Cancion) {
        val nuevos = favoritos.toMutableSet()
        if (!nuevos.add(cancion.id.toString())) {
            nuevos.remove(cancion.id.toString())
        }
        favoritos = nuevos
        preferencias.edit()
            .putStringSet("favoritos", nuevos)
            .apply()
    }

    val lista = canciones.filter { cancion ->
        val coincide = cancion.titulo.contains(
            busqueda, ignoreCase = true
        ) || cancion.artista.contains(busqueda, ignoreCase = true)

        coincide && (pestaña != 2 ||
            favoritos.contains(cancion.id.toString()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Morado, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "NOX MUSIC",
                    color = Texto,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "TU MÚSICA, TU UNIVERSO",
                    color = Secundario,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
            }

            IconButton(onClick = {
                if (permisoConcedido) canciones = cargarMusica()
                else solicitarPermiso.launch(permiso)
            }) {
                Icon(Icons.Default.Refresh, "Actualizar", tint = Morado)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            when (pestaña) {
                1 -> "Toda tu biblioteca"
                2 -> "Tus favoritos"
                else -> "Escucha lo que amas"
            },
            color = Texto,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar canciones o artistas") },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = Morado)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Texto,
                unfocusedTextColor = Texto,
                focusedBorderColor = Morado,
                unfocusedBorderColor = Panel,
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel
            )
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Inicio", "Biblioteca", "Favoritos").forEachIndexed {
                    indice, nombre ->
                FilterChip(
                    selected = pestaña == indice,
                    onClick = { pestaña = indice },
                    label = { Text(nombre) },
                    leadingIcon = {
                        Icon(
                            when (indice) {
                                0 -> Icons.Default.Home
                                1 -> Icons.Default.LibraryMusic
                                else -> Icons.Default.Favorite
                            },
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Morado,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!permisoConcedido) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = Morado,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Tu música te espera",
                    color = Texto,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Permite el acceso a los archivos de audio.",
                    color = Secundario
                )
                Button(
                    onClick = { solicitarPermiso.launch(permiso) }
                ) {
                    Text("Dar permiso")
                }
            }
        } else if (lista.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        null,
                        tint = Morado,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (canciones.isEmpty())
                            "No encontramos canciones"
                        else "No hay resultados",
                        color = Texto,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Guarda archivos de audio en tu teléfono",
                        color = Secundario
                    )
                }
            }
        } else {
            Text(
                "${lista.size} canciones",
                color = Secundario,
                fontSize = 13.sp
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(lista, key = { it.id }) { cancion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(16.dp))
                            .clickable { reproducir(cancion) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    Color(0xFF302044),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                null,
                                tint = Morado
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                cancion.titulo,
                                color = Texto,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                cancion.artista,
                                color = Secundario,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { cambiarFavorito(cancion) }
                        ) {
                            Icon(
                                if (favoritos.contains(cancion.id.toString()))
                                    Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = Morado
                            )
                        }
                    }
                }
            }
        }

        actual?.let { cancion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF251A35),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Album,
                    null,
                    tint = Morado,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        cancion.titulo,
                        color = Texto,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        cancion.artista,
                        color = Secundario,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = {
                    reproductor.seekToPreviousMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        null,
                        tint = Texto
                    )
                }

                IconButton(onClick = {
                    if (reproductor.isPlaying) reproductor.pause()
                    else reproductor.play()
                }) {
                    Icon(
                        if (reproduciendo) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        "Reproducir o pausar",
                        tint = Morado,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = {
                    reproductor.seekToNextMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipNext,
                        null,
                        tint = Texto
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}
