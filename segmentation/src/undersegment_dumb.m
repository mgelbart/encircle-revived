clear all; close all; clc;

%%%%%
ERASE_EXISTING = 0;
WRITE_DATA = 1;
DATASET = 'fly';
IMAGE_FORMAT = 'png';
%%%%%


% delete all existing output files!!!
if ERASE_EXISTING && WRITE_DATA
%     make_clean(DATASET);
end


%{
% read in the sample image (in current directory)
x = double(imread('fly3.tif'))/256;

% call the script I use for EDGE
% the last parameter is the threshold. 0 is the default value.
% here, we use a higher threshold to produce an undersegmentation
y = get_membs_v3(x, 20, 70, .5);
 
% show the undersegmentation as an overlay over the original
% out(:,:,1) = x;
% out(:,:,3) = y;
% figure, imshow(out);

% label the regions and find the areas
z  = bwlabel(~y, 4);
a = regionprops(z, 'Area');
a = [a.Area];

% then, sort regions by area and remove the first two (background and smth)
s = flipud(sortrows([a(:) (1:length(a))']));
s(1:2,:) = [];

save temp;
%}
%%
load temp

if WRITE_DATA
%    meta_file = fopen(fullfile('..', 'meta', 'fly.txt'), 'w');
else
    disp('You have selected NOT to write any data!');
end

NUM_CONTOUR_POINTS = 100;
LABELING_RELIABILITY = 0.9;

% allows the user to label the images
% writes the images, labels, polygon contours to their respective files


h1 = figure(1);


for i = 1:size(s, 1)
    % make a binary mask for region i
    mask = z==s(i,2);
    
    % extract the portion of the original image inside the mask
    patch = x .* double(mask);
    
    % crop to a rectangular bounding box
    [yy xx] = find(patch);
    PAD = 1; 
    patch_crop = patch(min(yy):max(yy),min(xx):max(xx));
    mask_crop = mask(min(yy):max(yy),min(xx):max(xx));
    
    patch_crop_new = zeros(size(patch_crop)+2*PAD);
    mask_crop_new  = zeros(size(mask_crop)+2*PAD);
    
    patch_crop_new(1+PAD:end-PAD,1+PAD:end-PAD) = patch_crop;
    mask_crop_new(1+PAD:end-PAD,1+PAD:end-PAD) = mask_crop;
    
    patch_crop = patch_crop_new;
    mask_crop = mask_crop_new;
    
    % find the contour around the mask
    C = contourc(mask_crop, [0.5 0.5]);
    C(:,1) = [];

    % iteratively remove closeest points
    Corig = C; 
    

    while size(C, 2) > NUM_CONTOUR_POINTS
        %    plot([C(1,end) C(1,:)], [C(2,end) C(2,:)],'*-b', 'LineWidth', 2);
        % drawnow;
        distmat = sqrt(bsxfun(@minus, C(1,:), C(1,:).').^2 + bsxfun(@minus, C(2,:), C(2,:).').^2);
        distmat(logical(eye(size(distmat)))) = Inf;
        [~, minind] = min(distmat(:));
        [a b] = ind2sub(size(distmat), minind);
        C(:,a)=[];
    end
    % may want to add something so that if a contour point is at an extreme
    % of a dimension (e.g., the furthest to the left), we keep it. 
    % not a big deal though

    
    % display the image and get the label
    hold off;
    imshow(patch_crop);
    title(sprintf('%u of %u', i, size(s, 1)));
    hold on;
    plot([C(1,end) C(1,:)], [C(2,end) C(2,:)],'*-b', 'LineWidth', 2);
    plot([Corig(1,end) Corig(1,:)], [Corig(2,end) Corig(2,:)], '--r');
        
%     skip = 0;
%     label = [];
%     while isempty(label)
%         k = waitforbuttonpress;
%         label = get(h1, 'CurrentCharacter');
%         if label == ' ' % if you want to discard the image
%             label = NaN;
%             skip = 1;
%             break;
%         end
%         label = str2double(label);
%         if ((label < 0) || (label > 10)) || ~(label > 0 && label < 10)
%             label = [];
%         end
%     end    
%     cla;
%     title(sprintf('You labeled the last image with: %d',label), 'FontSize', 30);   
%     drawnow;
%     
%     if skip
%         continue
%     end
    
    % change the coordinate system of the countour so that zero is at the
    % center of the image
    % the +1 is needed because of how the contour coordinates and the image
    % coordinates work. in the contour coordinate system, the image goes
    % from 0.5 to N + 0.5, where N is the size of the image in that
    % dimension. therefore the center is (0.5+N+0.5)/2 = (N+1)/2. 
    % therefore, we center the contour at this value.
    C = bsxfun(@minus, C, (fliplr(size(patch_crop)).'+1)/2);
    % also, flip the y-coordinate to get the normal convention for drawing
    C(2,:) = -C(2,:);
   
%     if ~WRITE_DATA
%         continue
%     end
    
%     fprintf(meta_file, '%04u\n', i);
    % write the image to a file
    imwrite(patch_crop, fullfile('..', 'images', 'fly', sprintf('%04u.%s', i, IMAGE_FORMAT)), IMAGE_FORMAT, 'Alpha', mask_crop);
  
%     keyboard
%   continue 
    % write the contour to a file
    polygon_file = fopen(fullfile('..', 'polygons', 'fly', sprintf('%04u.txt', i)), 'w');
    
    % write the size of the image ((x, y) not (y, x))
    % because this information is not implicit in the contour given that
    % the extremes of the contour may not touch the edges of the images.
    % this depends on the "PAD" factor earlier in this script.
    fprintf(polygon_file, '%d,%d\n', size(patch_crop,2), size(patch_crop,1));
    
    % write the number of points in the contour
    fprintf(polygon_file, '%d\n', size(C,2));
    
    % note that C does not need to be flipped because it is already in
    % (x, y) format unlike most things in matlab which are (y, x)[
    for j =  1:2
        for k = 1:size(C, 2);
            fprintf(polygon_file, '%.2f,', C(j, k));
        end
        fprintf(polygon_file, '\n');
    end
    fclose(polygon_file);
    
    % write an initial database entry to a file
    nsegments_file = fopen(fullfile('..', 'segment_likelihoods', 'fly', sprintf('%04u.txt', i)), 'w');
    fprintf(nsegments_file, '%u:%.2f,\n', label, LABELING_RELIABILITY);
    fprintf(nsegments_file, '\n');
    fclose(nsegments_file);
    
end
close(1)
% if WRITE_DATA
%     fclose(meta_file);
% end
   
