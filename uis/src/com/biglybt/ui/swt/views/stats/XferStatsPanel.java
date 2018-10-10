/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.views.stats;

import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.global.GlobalManagerStats.AggregateStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.FrequencyLimitedDispatcher;
import com.biglybt.core.util.TimerEventPeriodic;


import com.biglybt.ui.swt.utils.ColorCache;

public class
XferStatsPanel
{
	private static final int ALPHA_FOCUS = 255;
	private static final int ALPHA_NOFOCUS = 150;

	Display display;
	Composite parent;

	Canvas canvas;
	Scale scale;

	private boolean mouseLeftDown = false;
	private boolean mouseRightDown = false;
	private int xDown;
	private int yDown;

	private Image img;

	private int alpha = 255;

	private boolean autoAlpha = false;

	private GlobalManagerStats		gm_stats;

	private long	latest_sequence	= Long.MAX_VALUE;
	
	private FrequencyLimitedDispatcher	refresh_dispatcher = 
		new FrequencyLimitedDispatcher(
			new AERunnable(){
				
				@Override
				public void runSupport(){
					Utils.execSWTThread(
						new Runnable(){
							
							@Override
							public void run(){
								refresh();
							}
						});
				}
			},
			250 );

	int flag_width;
	int flag_height;

	static float def_minX = -1000;
	static float def_maxX = 1000;
	static float def_minY = -1000;
	static float def_maxY = 1000;
	static double def_rotation = 0;
	
	private static class Scale {
		int width;
		int height;
		float minX;
		float maxX;
		float minY;
		float maxY;
		double rotation;
		
		float saveMinX;
		float saveMaxX;
		float saveMinY;
		float saveMaxY;
		double saveRotation;

		{
			init();
		}
		
		private void
		init()
		{
			minX = def_minX;
			maxX = def_maxX;
			minY = def_minY;
			maxY = def_maxY;
			rotation = def_rotation;
			
			saveMinX	= 0;
			saveMaxX	= 0;
			saveMinY	= 0;
			saveMaxY	= 0;
			saveRotation	= 0;
		}
		
		public int getX(float x,float y) {
			return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
		}

		public int getY(float x,float y) {
			return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
		}
		
		public int[] getXY( float x, float y ){
			return( new int[]{getX(x,y), getY( x,y)});
		}
			
		public int getWidth( float w ){
			return( (int)(w/(maxX-minX)* width));
		}		
		
		public int getHeight( float w ){	
			return( (int)(w/(maxY-minY)* height));
		}
		
		public int getReverseWidth( float w ){
			return( (int)((w/width)* (maxX-minX)));
		}	
		
		public int getReverseHeight( float h ){
			return( (int)((h/height)* (maxY-minY)));
		}	
	}

	public XferStatsPanel(Composite parent) {
		this.parent = parent;
		this.display = parent.getDisplay();
		this.canvas = new Canvas(parent,SWT.NO_BACKGROUND);

		this.scale = new Scale();

		canvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (img != null && !img.isDisposed()) {
					Rectangle bounds = img.getBounds();
					if (bounds.width >= e.width && bounds.height >= e.height) {
						if (alpha != 255) {
							try {
								e.gc.setAlpha(alpha);
							} catch (Exception ex) {
								// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
							}
						}
						e.gc.drawImage(img, e.x, e.y, e.width, e.height, e.x, e.y,
								e.width, e.height);
					}
				} else {
					e.gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND));
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);

					e.gc.drawText(
							MessageText.getString( "v3.MainWindow.view.wait"), 10,
							10, true);
				}
			}
		});

		canvas.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseDown(MouseEvent event) {
				if(event.button == 1) mouseLeftDown = true;
				if(event.button == 3) mouseRightDown = true;
				xDown = event.x;
				yDown = event.y;
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;
				scale.saveRotation = scale.rotation;
			}

			@Override
			public void mouseUp(MouseEvent event) {
				if(event.button == 1) mouseLeftDown = false;
				if(event.button == 3) mouseRightDown = false;
				refresh();
			}
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				scale.init();
				refresh();
			}
		});

		canvas.addListener(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
			}
		});

		canvas.addListener(SWT.MouseWheel, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// System.out.println(event.count);
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;

				int deltaY = event.count * 5;
				// scaleFactor>1 means zoom in, this happens when
				// deltaY<0 which happens when the mouse is moved up.
				float scaleFactor = 1 - (float) deltaY / 300;
				if(scaleFactor <= 0) scaleFactor = 0.01f;

				// Scalefactor of e.g. 3 makes elements 3 times larger
				float moveFactor = 1 - 1/scaleFactor;

				float centerX = (scale.saveMinX + scale.saveMaxX)/2;
				scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
				scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

				float centerY = (scale.saveMinY + scale.saveMaxY)/2;
				scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
				scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
				refresh();
			}
		});

		canvas.addMouseMoveListener(new MouseMoveListener() {
			@Override
			public void mouseMove(MouseEvent event) {
				
				if(mouseLeftDown && (event.stateMask & SWT.MOD4) == 0) {
					int deltaX = event.x - xDown;
					int deltaY = event.y - yDown;
					float width = scale.width;
					float height = scale.height;
					float ratioX = (scale.saveMaxX - scale.saveMinX) / width;
					float ratioY = (scale.saveMaxY - scale.saveMinY) / height;
					float realDeltaX = deltaX * ratioX;
					float realDeltaY  = deltaY * ratioY;
					scale.minX = scale.saveMinX - realDeltaX;
					scale.maxX = scale.saveMaxX - realDeltaX;
					scale.minY = scale.saveMinY - realDeltaY;
					scale.maxY = scale.saveMaxY - realDeltaY;
					requestRefresh();
				}
				if(mouseRightDown || (mouseLeftDown && (event.stateMask & SWT.MOD4) > 0)) {
					int deltaX = event.x - xDown;
					scale.rotation = scale.saveRotation - (float) deltaX / 100;

					int deltaY = event.y - yDown;
					// scaleFactor>1 means zoom in, this happens when
					// deltaY<0 which happens when the mouse is moved up.
					float scaleFactor = 1 - (float) deltaY / 300;
					if(scaleFactor <= 0) scaleFactor = 0.01f;

					// Scalefactor of e.g. 3 makes elements 3 times larger
					float moveFactor = 1 - 1/scaleFactor;

					float centerX = (scale.saveMinX + scale.saveMaxX)/2;
					scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
					scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

					float centerY = (scale.saveMinY + scale.saveMaxY)/2;
					scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
					scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
					requestRefresh();
				}
			}
		});

		canvas.addMouseTrackListener(new MouseTrackListener() {
			@Override
			public void mouseHover(MouseEvent e) {
			}

			@Override
			public void mouseExit(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_NOFOCUS);
				}
			}

			@Override
			public void mouseEnter(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_FOCUS);
				}
			}
		});
		
		parent.addListener(
			SWT.Resize,
			new Listener(){
				
				@Override
				public void handleEvent(Event event){
					requestRefresh();
				}
			});
	}

	public void setLayoutData(Object data) {
		canvas.setLayoutData(data);
	}

	protected void
	init(
		GlobalManagerStats		_stats )
	{
		gm_stats	= _stats;
	}
	
	public void
	refreshView()
	{
		if ( gm_stats == null ){
			
			return;
		}
		
		AggregateStats		a_stats = gm_stats.getAggregateRemoteStats();
			
		if ( latest_sequence == Long.MAX_VALUE || latest_sequence != a_stats.getSequence()){
		
			refresh();
		}
	}

	private void
	requestRefresh()
	{
		refresh_dispatcher.dispatch();
	}
	
	public void
	refresh()
	{
		if ( canvas.isDisposed()){

			return;
		}

		Rectangle size = canvas.getBounds();

		if ( size.width <= 0 || size.height <= 0 ){

			return;
		}

		scale.width 	= size.width;
		scale.height 	= size.height;

		if (img != null && !img.isDisposed()){

			img.dispose();
		}

		img = new Image(display,size);

		GC gc = new GC(img);

		gc.setAdvanced( true );

		gc.setAntialias( SWT.ON );
		gc.setTextAntialias( SWT.ON );

		Color white = ColorCache.getColor(display,255,255,255);
		gc.setForeground(white);
		gc.setBackground(white);
		gc.fillRectangle(size);

		if ( gm_stats == null ){
			
			return;
		}
		
		gc.setForeground(Colors.black);

		flag_width	= scale.getReverseWidth( 25 );
		flag_height	= scale.getReverseHeight( 15 );
				

		AggregateStats		a_stats = gm_stats.getAggregateRemoteStats();
		
		latest_sequence = a_stats.getSequence();
		
		gc.drawText( 
			" samples=" + a_stats.getSamples() + ", population=" + a_stats.getEstimatedPopulation(), 
			scale.getX( scale.minX, scale.minY), scale.getY(scale.minX, scale.minY) );
		
		Map<String,Map<String,Long>> stats = a_stats.getStats();
						
		List<Node> 			origins 	= new ArrayList<>();
		Map<String,Node>	dest_map 	= new HashMap<>();
		
		
		for ( Map.Entry<String,Map<String,Long>> entry: stats.entrySet()){
					
			String from_cc = entry.getKey();
						
			Node from_node 	= new Node();
			
			origins.add( from_node );
			
			Image from_image = ImageRepository.getCountryFlag( from_cc, false );

			from_node.cc		= from_cc;
			from_node.image		= from_image;
			from_node.links		= new ArrayList<>();
						
			for ( Map.Entry<String,Long>	entry2: entry.getValue().entrySet()){
				
				String 	to_cc 		= entry2.getKey();
				long	to_count	= entry2.getValue();
				
				Node	to_node = dest_map.get( to_cc );
				
				if ( to_node == null ){
					
					to_node = new Node();
					
					dest_map.put( to_cc,  to_node );
					
					Image to_image = ImageRepository.getCountryFlag( to_cc, false );

					to_node.cc		= to_cc;
					to_node.image	= to_image;
				}
				
				from_node.count += to_count;
				to_node.count += to_count;
				
				Link link = new Link();
				
				link.count += to_count;
				link.source	= from_node;
				link.target	= to_node;
				
				from_node.links.add( link );
			}
		}
		
		Comparator<Node> comp = new Comparator<Node>()
		{
			@Override
			public int compare(Node o1, Node o2){
				return( Long.compare(o2.count, o1.count ));
			}
		};
		
		List<Node>	dests = new ArrayList<>( dest_map.values());
		
		Collections.sort( origins, comp );
		
		Collections.sort( dests, comp );

		
		int flag_x 	= (int)( scale.minX + flag_width );
		int flag_y	= (int)( scale.minY + flag_height*2 );
		
		int	flag_x_start = flag_x;
		
		for ( Node node: dests ){
			
			node.x_pos	= flag_x;
			node.y_pos	= flag_y;
						
			if ( flag_x > ( scale.maxX - flag_width )){
				
				node.hidden = true;
				
			}else{
			
				flag_x += flag_width * 2;
			}
		}

		int	pad = (int)(( scale.maxX - scale.minX - ( flag_x - flag_x_start ))/2);
				
		boolean odd = false;

		for ( Node node: dests ){

			if ( node.hidden ){
				
				break;
			}
			
			node.x_pos += pad;
			
			node.draw( gc, odd  );
			
			odd = !odd;
		}
		
		flag_x 	= (int)( scale.minX + flag_width );
		flag_y	= (int)( scale.maxY - 3*flag_height );

		flag_x_start = flag_x;
		
		for ( Node node: origins ){
			
			node.x_pos	= flag_x;
			node.y_pos	= flag_y;
						
			if ( flag_x > ( scale.maxX - flag_width )){
				
				node.hidden = true;
				
			}else{
			
				flag_x += flag_width * 2;
			}
		}
		
		pad = (int)(( scale.maxX - scale.minX - ( flag_x - flag_x_start ))/2);
		
		odd = true;

		for ( Node node: origins ){

			if ( node.hidden ){
				
				break;
			}
			
			node.x_pos += pad;
		
			node.draw( gc, odd );
			
			odd = !odd;
			
			for ( Link link: node.links ){
				
				link.draw(gc);
			}
		}
		
		gc.dispose();

		canvas.redraw();
	}



	public int getAlpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = alpha;
		if (canvas != null && !canvas.isDisposed()) {
			canvas.redraw();
		}
	}

	public void setAutoAlpha(boolean autoAlpha) {
		this.autoAlpha = autoAlpha;
		if (autoAlpha) {
			setAlpha(canvas.getDisplay().getCursorControl() == canvas ? ALPHA_FOCUS : ALPHA_NOFOCUS);
		}
	}

	public void delete()
	{
		if(img != null && !img.isDisposed())
		{
			img.dispose();
		}
	}
	
	private class
	Link
	{
		Node	source;
		Node	target;
		long	count;
		
		private void
		draw(
			GC		gc )
		{
			if ( source.hidden || target.hidden ){
				
				return;
			}
			
			int x1 = source.x_pos + flag_width/2;
			int y1 = source.y_pos - flag_height;
			
			int x2 = target.x_pos + flag_width/2;
			int y2 = target.y_pos + 2*flag_height;
			
			int[] xy1 = scale.getXY( x1, y1 );
			int[] xy2 = scale.getXY( x2, y2 );
			
			gc.drawLine(xy1[0],xy1[1],xy2[0],xy2[1] );
		}
	}
	
	private class
	Node
	{
		String			cc;
		Image			image;
		long			count;
		
		List<Link>		links;
		
		int			x_pos;
		int			y_pos;
		boolean		hidden;
		
		private void
		draw(
			GC			gc,
			boolean		odd )
		{
			if ( image == null ){
				
				gc.drawText( cc, scale.getX( x_pos, y_pos), scale.getY( x_pos, y_pos) );

			}else{
				
				gc.drawImage( image, scale.getX( x_pos, y_pos), scale.getY( x_pos, y_pos) );
			}

			int[] xy = scale.getXY( x_pos, odd?(y_pos+flag_height):(y_pos-flag_height));
			
				// remember stats are in k per min
			
			gc.drawText( DisplayFormatters.formatByteCountToKiBEtcPerSec( count*1024/60 ), xy[0], xy[1] );
				
		}
	}
}