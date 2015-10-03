package net.caseif.advent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class Advent extends JavaPlugin implements Listener {
	public static Advent plugin;
	static final Logger log = Logger.getLogger("Minecraft");
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		Advent.plugin = this;

		// create the data folder
		this.getDataFolder().mkdir();

		// create the variable storage file  
		File file = new File(getDataFolder(), "lastfill.txt");
		if (!(file.exists())) {
			try {
				file.createNewFile();
				FileWriter fw = new FileWriter(file, true);
				@SuppressWarnings("resource")
				PrintWriter pw = new PrintWriter(fw);
				pw.println("0");	 
				pw.flush();
			}
			catch (IOException e){
				e.printStackTrace();
			}
		}

		// create the plugin table if it does not exist
		Connection conn = null;
		Statement st = null;
		try{
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "chestdata.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			st.executeUpdate("CREATE TABLE IF NOT EXISTS chestdata (" +
					"id INTEGER NOT NULL PRIMARY KEY," +
					"username VARCHAR(20) NOT NULL," +
					"x INTEGER NOT NULL," +
					"y INTEGER NOT NULL," +
					"z INTEGER NOT NULL)");
		}
		catch (Exception e){
			e.printStackTrace();
		}
		finally {
			try {
				st.close();
				conn.close();
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		log.info(this + " has been enabled!");
	}
	public void onDisable(){
		log.info(this + " has been disabled!");
	}

	// initiate function for detecting player clicking sign
	@EventHandler(priority = EventPriority.MONITOR)
	public void onRightClick(PlayerInteractEvent e){
		// check for click
		if ((e.getAction() == Action.RIGHT_CLICK_BLOCK) || (e.getAction() == Action.RIGHT_CLICK_AIR)){
			// check if clicked block is sign
			if (e.getClickedBlock() != null){
				if (e.getClickedBlock().getType() == Material.WALL_SIGN){
					Player player = e.getPlayer();
					String p = player.getName();
					Sign sign = (Sign) e.getClickedBlock().getState();
					String fline = sign.getLine(0);
					if (fline.equalsIgnoreCase("§4[Advent]")){
						Connection conn = null;
						ResultSet rs = null;
						Statement st = null;
						try{
							Class.forName("org.sqlite.JDBC");
							String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator +
									"chestdata.db";
							conn = DriverManager.getConnection(dbPath);
							st = conn.createStatement();
							rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE username = '" + p +
									"'"); 
							int count = 0;
							while (rs.next()){
								count = rs.getInt(1);
							}
							if (count == 0){
								int checkX = e.getClickedBlock().getX();
								int checkY = e.getClickedBlock().getY() - 1;
								int checkZ = e.getClickedBlock().getZ();
								rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" + checkX +
										"' AND y = '" + checkY + "' AND z = '" + checkZ + "'");
								int regcount = 0;
								while (rs.next()){
									regcount = rs.getInt(1);
								}
								if (regcount == 0){
									sign.setLine(2, "");
									sign.setLine(3, "§2" + p);
									sign.update();
									int signX = sign.getX();
									int signY = sign.getY();
									int signZ = sign.getZ();
									int chestY = signY - 1;
									Location chestloc = new Location(player.getWorld(), signX, chestY, signZ);
									if (chestloc.getBlock().getType() == Material.AIR){
										chestloc.getBlock().setType(Material.CHEST);
										st.executeUpdate("INSERT INTO chestdata (username, x, y, z) " +
												"VALUES ('" + p +
												"', '" + signX + "', '" + chestY + "', '" + signZ + "')");
										player.sendMessage(ChatColor.DARK_RED + "Happy " +
												ChatColor.DARK_GREEN +
												"Advent!");
									}
									else {
										player.sendMessage(ChatColor.RED +
												"Error: Block below sign must be non-solid!");
									}
								}
								else {
									rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" +
											checkX +
											"' AND y = '" + checkY + "' AND z = '" + checkZ +
											"' AND username = '" +
											p + "'");
									int pcount = 0;
									while (rs.next()){
										pcount = rs.getInt(1);
									}
									if (pcount == 0){
										player.sendMessage(ChatColor.RED + "This Advent Chest is already " +
												"claimed!");
									}
								}
							}
							else {
								player.sendMessage(ChatColor.RED + "You already have an Advent Chest!");
							}
						}
						catch(Exception q){
							q.printStackTrace();
						}
						finally {
							try {
								rs.close();
								st.close();
								conn.close();
							}
							catch (Exception g){
								g.printStackTrace();
							}
						}
					}
				}
			}
		}
	}

	// unlink sign if chest is destroyed
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockDestroy(BlockBreakEvent b){
		if (b.getBlock().getType() == Material.CHEST){
			int blockX = b.getBlock().getX();
			int blockY = b.getBlock().getY();
			int blockZ = b.getBlock().getZ();
			Player player = b.getPlayer();
			String p = player.getName();
			try{
				Connection conn = null;
				Statement st = null;
				ResultSet rs = null;
				Class.forName("org.sqlite.JDBC");
				String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "chestdata.db";
				conn = DriverManager.getConnection(dbPath);
				st = conn.createStatement();
				rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" + blockX + "' AND y = '" +
						blockY +
						"' AND z = '" + blockZ + "'");
				int count = 0;
				while (rs.next()){
					count = rs.getInt(1);
				}
				if (count == 1){
					rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" + blockX + "' AND " +
							"y = '" +
							blockY + "' AND z = '" + blockZ + "' AND username = '" + p + "'");
					int newcount = 0;
					while (rs.next()){
						newcount = rs.getInt(1);
					}
					if (newcount == 0 || player.isOp()){
						int signY = blockY + 1;
						Location signLoc = new Location(player.getWorld(), blockX, signY, blockZ);
						if (signLoc.getBlock().getType() == Material.WALL_SIGN){
							Sign sign = (Sign)signLoc.getBlock().getState();
							sign.setLine(2, "§2Claim this");
							sign.setLine(3, "§2sign!");
							sign.update();
							st.executeUpdate("DELETE FROM chestdata WHERE username = '" + p + "'");
							player.sendMessage(ChatColor.RED + "Advent Chest destroyed");
						}
					}
					else {
						player.sendMessage(ChatColor.RED + "You don't own that Advent Chest!");
					}
				}
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		if (b.getBlock().getType() == Material.WALL_SIGN){
			Sign sign = (Sign)(b.getBlock().getState());
			if (sign.getLine(0).equalsIgnoreCase("§4[Advent]")){
				if (!b.getPlayer().isOp()){
					b.setCancelled(true);
					b.getPlayer().sendMessage(ChatColor.RED +
							"You don't have permission to break this sign!");
				}
			}
		}
	}

	// disallow access to others' Advent Chests
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChestOpen(PlayerInteractEvent o){
		if (o.getAction() == Action.RIGHT_CLICK_BLOCK){
			if (o.getClickedBlock().getType() == Material.CHEST){
				Player player = o.getPlayer();
				String p = player.getName();
				Chest chest = (Chest) o.getClickedBlock().getState();
				int chestX = chest.getBlock().getX();
				int chestY = chest.getBlock().getY();
				int chestZ = chest.getBlock().getZ();
				try{
					Connection conn = null;
					Statement st = null;
					ResultSet rs = null;
					Class.forName("org.sqlite.JDBC");
					String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "chestdata.db";
					conn = DriverManager.getConnection(dbPath);
					st = conn.createStatement();
					rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" + chestX +
							"' AND y = '" +
							chestY + "' AND z = '" + chestZ + "'");
					int count = 0;
					while (rs.next()){
						count = rs.getInt(1);
					}
					if (count == 1){
						rs = st.executeQuery("SELECT COUNT(*) FROM chestdata WHERE x = '" + chestX +
								"' AND y = '" +
								chestY + "' AND z = '" + chestZ + "' AND username = '" + p + "'");
						int seccount = 0;
						while (rs.next()){
							seccount = rs.getInt(1);
						}
						if (seccount == 0){
							if (!o.getPlayer().isOp()){
								o.setCancelled(true);
								player.sendMessage(ChatColor.RED +
										"You don't have permission to open that Advent Chest!");
							}
						}
					}
				}
				catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	// check if placed sign meets criteria
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void eventSignChanged(SignChangeEvent p){
		String line = p.getLine(0);
		if (line.equalsIgnoreCase("[Advent]")){
			if (p.getPlayer().isOp()){
				p.setLine(0, "§4[Advent]");
				p.setLine(2, "§2Claim this");
				p.setLine(3, "§2sign!");
			}
		}
		if (line.equalsIgnoreCase("§4[Advent]")){
			if (!p.getPlayer().isOp()){
				p.setLine(0, "[Advent]");
			}
		}
	}

	// read the text file
	public String readFile(){
		File file = new File(getDataFolder(), "lastfill.txt");
		URI uri = file.toURI();
		byte[] bytes = null;
		String last = null;
		try {
			bytes = Files.readAllBytes(Paths.get(uri));
			last = new String(bytes);
		}
		catch (IOException e){
			e.printStackTrace();
		}
		return last;
	}

	// call the chest filling functions
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerJoin(PlayerJoinEvent j) throws IOException {
		Calendar cal = Calendar.getInstance();
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int month = cal.get(Calendar.MONTH);
		if (Integer.parseInt(readFile().replaceAll("(\\r|\\n)", "")) < day){
			File file = new File(getDataFolder(), "lastfill.txt");
			if (month == 11 && day >= 1 || day <= 25){
				ItemStack gift = null;
				ItemStack[] gifts = null;
				if (day == 25){
					List<ItemStack> xmasGifts = new ArrayList<ItemStack>();
					for (String k : getConfig().getKeys(false)){
						if (k.startsWith("xmas")){
							ItemStack is = null;
							if (getConfig().getString(k + ".item").equalsIgnoreCase("SANTA_SUIT")){
								ItemStack h = new ItemStack(Material.LEATHER_HELMET);
								ItemStack c = new ItemStack(Material.LEATHER_CHESTPLATE);
								ItemStack l = new ItemStack(Material.LEATHER_LEGGINGS);
								ItemStack b = new ItemStack(Material.LEATHER_BOOTS);
								LeatherArmorMeta hMeta = (LeatherArmorMeta)h.getItemMeta();
								LeatherArmorMeta cMeta = (LeatherArmorMeta)c.getItemMeta();
								LeatherArmorMeta lMeta = (LeatherArmorMeta)l.getItemMeta();
								LeatherArmorMeta bMeta = (LeatherArmorMeta)b.getItemMeta();
								hMeta.setColor(Color.RED);
								cMeta.setColor(Color.RED);
								lMeta.setColor(Color.RED);
								bMeta.setColor(Color.BLACK);
								h.setItemMeta(hMeta);
								c.setItemMeta(cMeta);
								l.setItemMeta(lMeta);
								b.setItemMeta(bMeta);
								xmasGifts.add(h);
								xmasGifts.add(c);
								xmasGifts.add(l);
								xmasGifts.add(b);
							}
							else {
								if (getConfig().getString(k + ".item").equalsIgnoreCase("RANDOM_DISC"))
									is = new ItemStack(Material.getMaterial(2256 + (int)(Math.random() *
											((2267 - 2256) + 1))),
											1);
								else if (getConfig().getString(k + ".item").equalsIgnoreCase("FESTIVE_WOOL")){
									xmasGifts.add(new ItemStack(Material.WOOL,
											getConfig().getInt(k + ".quantity"), (short)14));
									xmasGifts.add(new ItemStack(Material.WOOL,
											getConfig().getInt(k + ".quantity"), (short)5));
								}
								else if (getConfig().getString(k +
										".item").equalsIgnoreCase("COOKIES_AND_MILK")){
									xmasGifts.add(new ItemStack(Material.COOKIE,
											getConfig().getInt(k + ".quantity")));
									xmasGifts.add(new ItemStack(Material.MILK_BUCKET,
											getConfig().getInt(k + ".milkquantity")));
								}
								else if (Advent.isInt(getConfig().getString(k + ".item")))
									is = new ItemStack(getConfig().getInt("1.item"),
											getConfig().getInt("1.quantity"));
								else
									is = new ItemStack(Material.getMaterial(
											getConfig().getString(k + ".item")),
											getConfig().getInt(k + ".quantity"));
								is.setDurability((short)getConfig().getInt(k + ".data"));
								ItemMeta meta = is.getItemMeta();
								if (!getConfig().getString(k + ".displayname").isEmpty())
									meta.setDisplayName(getConfig().getString(k + ".displayname"));
								if (!getConfig().getStringList(k + ".lore").isEmpty())
									meta.setLore(getConfig().getStringList(k + ".lore"));
								if (meta instanceof LeatherArmorMeta &&
										!getConfig().getString(k + ".armorcolor").isEmpty())
									((LeatherArmorMeta)meta).setColor(Color.fromRGB(getConfig().getInt(k +
											".armorcolor.red"),
											getConfig().getInt(k + ".armorcolor.green"),
											getConfig().getInt(k + ".armorcolor.blue")));
								if (meta instanceof SkullMeta && !getConfig().getString(k +
										".skullowner").isEmpty())
									((SkullMeta)meta).setOwner(getConfig().getString(k + ".skullowner"));
								is.setItemMeta(meta);
								if (is != null)
									xmasGifts.add(is);
							}
						}
					}
					gifts = new ItemStack[xmasGifts.size()];
					for (int i = 0; i < xmasGifts.size(); i++)
						gifts[i] = xmasGifts.get(i);
				}
				else if (getConfig().getString(day + ".item").equalsIgnoreCase("SANTA_SUIT")){
					ItemStack h = new ItemStack(Material.LEATHER_HELMET);
					ItemStack c = new ItemStack(Material.LEATHER_CHESTPLATE);
					ItemStack l = new ItemStack(Material.LEATHER_LEGGINGS);
					ItemStack b = new ItemStack(Material.LEATHER_BOOTS);
					LeatherArmorMeta hMeta = (LeatherArmorMeta)h.getItemMeta();
					LeatherArmorMeta cMeta = (LeatherArmorMeta)c.getItemMeta();
					LeatherArmorMeta lMeta = (LeatherArmorMeta)l.getItemMeta();
					LeatherArmorMeta bMeta = (LeatherArmorMeta)b.getItemMeta();
					hMeta.setColor(Color.RED);
					cMeta.setColor(Color.RED);
					lMeta.setColor(Color.RED);
					bMeta.setColor(Color.BLACK);
					h.setItemMeta(hMeta);
					c.setItemMeta(cMeta);
					l.setItemMeta(lMeta);
					b.setItemMeta(bMeta);
					gifts = new ItemStack[]{h, c, l, b};
				}
				else {
					if (getConfig().getString(day + ".item").equalsIgnoreCase("RANDOM_DISC"))
						gift = new ItemStack(Material.getMaterial(2256 + (int)(Math.random() *
								((2267 - 2256) + 1))),
								1);
					else if (getConfig().getString(day + ".item").equalsIgnoreCase("FESTIVE_WOOL")){
						gifts = new ItemStack[]{new ItemStack(Material.WOOL,
								getConfig().getInt(day + ".quantity"), (short)14),
								new ItemStack(Material.WOOL, getConfig().getInt(day + ".quantity"),
										(short)5)};
					}
					else if (getConfig().getString(day + ".item").equalsIgnoreCase("COOKIES_AND_MILK")){
						gifts = new ItemStack[]{new ItemStack(Material.COOKIE,
								getConfig().getInt(day + ".quantity")),
								new ItemStack(Material.MILK_BUCKET,
										getConfig().getInt(day + ".milkquantity"))};
					}
					else if (Advent.isInt(getConfig().getString(day + ".item")))
						gift = new ItemStack(getConfig().getInt("1.item"), getConfig().getInt("1.quantity"));
					else
						gift = new ItemStack(Material.getMaterial(getConfig().getString(day + ".item")),
								getConfig().getInt(day + ".quantity"));
					gift.setDurability((short)getConfig().getInt(day + ".data"));
					ItemMeta meta = gift.getItemMeta();
					if (!getConfig().getString(day + ".displayname").isEmpty())
						meta.setDisplayName(getConfig().getString(day + ".displayname"));
					if (!getConfig().getStringList(day + ".lore").isEmpty())
						meta.setLore(getConfig().getStringList(day + ".lore"));
					if (meta instanceof LeatherArmorMeta &&
							!getConfig().getString(day + ".armorcolor").isEmpty())
						((LeatherArmorMeta)meta).setColor(Color.fromRGB(getConfig().getInt(day +
								".armorcolor.red"),
								getConfig().getInt(day + ".armorcolor.green"),
								getConfig().getInt(day + ".armorcolor.blue")));
					if (meta instanceof SkullMeta && !getConfig().getString(day + ".skullowner").isEmpty())
						((SkullMeta)meta).setOwner(getConfig().getString(day + ".skullowner"));
					gift.setItemMeta(meta);
					if (gift != null)
						gifts = new ItemStack[]{gift};
				}
				Connection conn = null;
				Statement st = null;
				ResultSet rs = null;
				try {
					Class.forName("org.sqlite.JDBC");
					String dbPath = "jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "chestdata.db";
					conn = DriverManager.getConnection(dbPath);
					st = conn.createStatement();
					rs = st.executeQuery("SELECT * FROM chestdata");
					while (rs.next()){
						int x = rs.getInt("x");
						int y = rs.getInt("y");
						int z = rs.getInt("z");
						World world = Bukkit.getServer().getWorlds().get(0);
						Block block = world.getBlockAt(x, y, z);
						BlockState state = block.getState();
						if (state instanceof Chest){
							Chest chest = (Chest)state;
							Inventory inv = chest.getInventory();
							for (ItemStack g : gifts)
								inv.addItem(g);
						}
					}
				}
				catch (Exception e){
					e.printStackTrace();
				}
				finally {
					try {
						conn.close();
						st.close();
						rs.close();
					}
					catch (Exception p){
						p.printStackTrace();
					}
				}
			}
			PrintWriter pw = new PrintWriter(file);
			pw.print(day);
			pw.close();
		}
	}

	public static boolean isInt(String s){
		try {
			Integer.parseInt(s);
			return true;
		}
		catch (NumberFormatException ex){
			return false;
		}
	}

}